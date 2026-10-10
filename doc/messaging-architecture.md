# Messaging service: architecture and tuning surface

Status: design note, 2026-09-28. Describes the `messaging` module as it runs today (branch
`feat/messaging`, merged into `announce-jo-branch`) so that it can be tuned. The functional guide is
`doc/messaging-context.md`; the framework it stands on is `doc/cqrs-reactive.md`.
A reader's version of this note, with a component diagram and the life of one message, is
`doc/messaging-architecture-guide.pdf`.

## 1. The service in one picture

```
 browser / app                     staff dashboard
   |  REST  |  WS /ws/conversations/{id}   |  WS /ws/staff?clientType=
   v        v                              v
+------------------------------------------------------------------------+
| messaging (Spring Boot 3.5, WebFlux on Netty, port 8086)               |
|                                                                        |
|  interfaces   ConversationController  AdminMessagingController         |
|               ConversationWebSocketHandler  StaffWebSocketHandler      |
|               Principals (X-User-Id / X-Staff-Id / X-Command-Id)       |
|                     |  commands                 |  queries              |
|  application  Conversations  Bulletins   ConversationQueries           |
|               AnnounceLookup (port) ----> announce-management HTTP      |
|                     |                                                  |
|  domain       Conversation (aggregate) ConversationHandlers  Rules     |
|                     |                                                  |
|  command side ReactiveCommandRouter --> OpenCQRS CommandRouter -------------> EventSourcingDB
|               R2dbcCommandIdStore (cqrs_command_log)                   |      /conversation/{id}
|                                                                        |
|  read side    3 event handling processors (fetch /conversation, rec.)  |<---- observe stream
|    conversation-read-model  -> announce_conversation, _participant,    |
|                                announce_chat_message         (MySQL)   |
|    messaging-public         -> Kafka messaging.v1  (OfferAcceptedV1..) |
|    messaging-live           -> Kafka messaging.live (MessageLive..)    |
|               R2dbcProgressTracker (cqrs_readside_progress)            |
|                                                                        |
|  subscribers  liveEvents-in-0  messaging.live (group = random per node)|
|                  -> LiveSessions -> LiveSink -> WebSocket send         |
|               orderEvents-in-0 order.v1 (group messaging)              |
|                  -> Conversations.postSystem (a command per record)    |
|               StreamBridgeLiveFeed: bulletins straight to messaging.live|
+------------------------------------------------------------------------+
       |                     |                          |
   MySQL (R2DBC pool)   EventSourcingDB (HTTP)     Kafka (SCSt binder)
```

Three external systems, four kinds of threads, and two independent data paths (command path and
fan-out path) meet in one JVM. Tuning is mostly about where those paths block and how many copies of
each processor run.

## 2. Components

### 2.1 Edges (`interfaces`)

| Component | Role | Notes for tuning |
|---|---|---|
| `ConversationController`, `AdminMessagingController` | REST over the command facade and the queries | Non-blocking on the Netty event loop until a command is dispatched |
| `ConversationWebSocketHandler` | one socket per (client, conversation); inbound frames become commands, outbound frames come from the `LiveSink` | Inbound frames are handled with `concatMap`, so one socket processes its own frames strictly one at a time; a slow command stalls that socket's later frames |
| `StaffWebSocketHandler` | staff dashboard sockets, bulletins by client type | |
| `Principals` | trusts the headers; no Spring Security in the module | Gateway must be the only caller |
| `LiveSessions` | in-memory registry: conversation id -> set of `LiveSink`, plus staff sinks | Pure JVM state; the node count multiplies nothing here, but each node must see every live record |
| `LiveSink` | `Sinks.many().unicast().onBackpressureBuffer()` per socket, `synchronized emit` | Unbounded buffer: a client that stops reading grows heap until the socket dies |

### 2.2 Application

| Component | Role | Notes |
|---|---|---|
| `Conversations` | command facade; resolves the seller through `AnnounceLookup`; `markRead` writes the read model directly; dispatches a command again when the event store refused its write (`messaging.command-retry.*`) | Every write except `markRead` is one command = one EventSourcingDB round trip, plus one per lost race |
| `ConversationQueries` | inbox, one conversation, message page, support queue; always the MySQL read model; `mayAttach` for the socket handshake looks an unknown conversation up again (`messaging.attach.*`) | `view()` does one participants query per conversation row, so an inbox page of 20 is 21 statements |
| `Bulletins` | writes `announce_bulletin`, publishes `BulletinLive` on the live topic | Not event-sourced |
| `AnnounceLookup` | `@HttpExchange` client to announce-management (`GET /api/announces/{id}`) via `HttpExchangeClients`: discovered, load-balanced, Resilience4j-wrapped; pinned by `cqrs.api.clients.announce-management.base-url` | On the path of `POST /api/conversations` for LISTING only |
| `MessagingProperties` | `max-text-length` 4000, `max-page-size` 200, `command-retry` (3 retries, 5 to 50 ms), `attach` (5 re-checks, 200 ms apart) | |

### 2.3 Domain

`Conversation` is an immutable record rebuilt from its events. It keeps participants, `lastSeq`, the
open and accepted offers, and `messagesByClientId`: a map of every client message id ever posted.
That map is what makes `PostMessage` idempotent, and it grows by one entry per message for the life
of the conversation. `ConversationHandlers` are OpenCQRS `CommandHandlerDefinition`s; each publishes at
most one event and returns the new state, so the REST and WebSocket answers never wait for the read
model.

### 2.4 Command side (`cqrs-reactive.command`)

1. `ReactiveCommandRouter.send` wraps the blocking OpenCQRS `CommandRouter` in `Mono.fromCallable`
   on `Schedulers.boundedElastic()` (default cap: 10 x CPU threads, 100 000 queued tasks).
2. OpenCQRS reads the subject's events from EventSourcingDB over its blocking HTTP client, replays
   them through the state rebuilding handlers, runs the command handler, and appends the new event
   with a precondition on the subject (optimistic concurrency). A write that loses raises
   `ConcurrencyException`; `Conversations` dispatches the command again (section 5, step 0 of section 6).
3. When the caller supplied `X-Command-Id`, `R2dbcCommandIdStore` claims the id in
   `cqrs_command_log` before the dispatch and stores the JSON result after it (two more MySQL
   statements per command). WebSocket frames never pass a command id; they rely on `clientMsgId`
   inside the aggregate instead.

`opencqrs.command-handling.cache.ref` is not set, so the framework's `StateRebuildingCache` is whatever
OpenCQRS defaults to (the metadata offers `LruInMemoryStateRebuildingCache(int)` and
`NoStateRebuildingCache`; verify the default before relying on it). Without a cache, every post to a
conversation with N messages replays N events from EventSourcingDB.

### 2.5 Read side (`cqrs-reactive.readside`)

Three OpenCQRS event handling processors, one per group, each with `fetch.subject=/conversation`
recursive:

| Group | Handler | Writes |
|---|---|---|
| `conversation-read-model` | `ConversationReadModelProjection` | the three MySQL tables, in a `TransactionalOperator` transaction per event |
| `messaging-public` | `PublicEventRelay` | `StreamBridge.send` on `messaging-out-0` |
| `messaging-live` | `PublicEventRelay` | `StreamBridge.send` on `messaging-live-out-0`, key = subject |

Mechanics that matter for tuning:

- Each processor runs on its own thread and calls the handler synchronously; the reactive handler is
  awaited with `cqrs.readside.handler-timeout` (30 s). Throughput of a group is therefore
  `1 / (handler latency + checkpoint latency)` per partition.
- `life-cycle.partitions` is 1 (default) for every group: one thread, strictly ordered over all
  conversations. Sequence resolution is `per_second_level_subject`, meaning ordering is only required
  per `/conversation/{id}`, so partitions can be raised without breaking order.
- `R2dbcProgressTracker.proceed` writes the checkpoint after every event: read-model write and
  checkpoint are two round trips to MySQL per event per group, so three groups cost roughly four
  MySQL statements plus two Kafka sends per `MessagePosted`.
- `life-cycle.controller=application_context`: every node starts every processor. With two nodes, both
  run `conversation-read-model` against the same checkpoint row, both publish to Kafka. The outcome is
  still correct (idempotent upserts, at-least-once on the topic) but the work is duplicated N times
  and the live topic carries N copies of each record. OpenCQRS also offers a lock-registry controller
  (leader election per group and partition), which is the multi-node setting to use.
- Retry: exponential back-off 500 ms to 30 s, max elapsed 1 h, then the processor gives up and the
  group stalls until restart (see the memory note on `cqrs-reactive`).

### 2.6 Fan-out path (`infrastructure.live`)

Sequence for one text message from client A to client B on another node:

1. A sends `{"type":"post"}` on its socket. Netty -> handler -> `Conversations.post` -> bounded-elastic
   thread -> EventSourcingDB replay + append -> `ack` frame back to A.
2. The `messaging-live` processor observes `MessagePosted`, maps it to `MessageLive`, sends it to Kafka
   `messaging.live` (key = `/conversation/{id}`), then writes its checkpoint.
3. Every node's `liveEvents` consumer (own consumer group `messaging-live-<uuid>`, `start-offset:
   latest`) receives the record on a binder thread, `LiveSessions.toConversation` pushes the JSON into
   each `LiveSink` of that conversation on that node, and the WebSocket `send` flux delivers it.

Delivery latency is the sum of: EventSourcingDB commit, processor pick-up (the observe stream),
Kafka produce (producer `linger.ms`/batching), Kafka consume (`fetch.min.bytes`, poll interval), and
the socket write. The sender's own `message` frame also travels this whole loop; the `ack` is the only
thing answered locally.

Bulletins skip the event store: `StreamBridgeLiveFeed` publishes `BulletinLive` directly on the same
binding, from a bounded-elastic thread.

Per-node random consumer groups are simple, but each restart leaves a dead group in Kafka until
`offsets.retention.minutes` expires it, and a node that restarts misses records published while it was
down (correct, since it holds no sockets then).

### 2.7 Public events and subscriptions

- `messaging.v1` out: `ConversationOpenedV1`, `OfferAcceptedV1` (consumed by the order module).
- `order.v1` in: `OrderAgreedV1`, `OrderPaidV1`, `OrderShippedV1`, `OrderSettledV1` become SYSTEM
  messages. Each record is one command on the binder thread, awaited with
  `cqrs.messaging.handler-timeout` (30 s); the consumer group `messaging` has the binder default
  concurrency of 1 and no DLQ configured, so a poisoned record blocks the partition after retries.

### 2.8 Persistence and connections

| Resource | Client | Used by | Setting today |
|---|---|---|---|
| MySQL `announce_reactive_db` | R2DBC pool from reactive-mybatis-support, shared with Spring Data R2DBC | read model, checkpoints, command log, read markers, bulletins | `initial-size 2`, `max-size 20` |
| EventSourcingDB | OpenCQRS blocking HTTP client | every command; three observe streams | `esdb.server.uri`, token |
| Kafka | Spring Cloud Stream Kafka binder | one producer (two output bindings), two consumer bindings | `auto-create-topics`, replication 1 |

### 2.9 Observability

Actuator `health,info,metrics,prometheus`; correlation and causation ids through `X-Correlation-Id` and
event metadata; `CqrsTracing` spans `cqrs.publish`, `cqrs.projection`, `cqrs.subscribe`. The
messaging-specific meters (command latency, fan-out latency, projection delay and handler time, sockets,
outbound buffers) are listed in section 5, together with the load driver that exercises them.

## 3. Thread and capacity model

| Pool | Size | Work | Blocks on |
|---|---|---|---|
| Netty event loop | CPU count | HTTP, WebSocket frames, R2DBC callbacks | nothing (must stay that way) |
| Reactor bounded-elastic | 10 x CPU, queue 100 000 | command dispatch, bulletin publish | EventSourcingDB HTTP, state replay |
| OpenCQRS processors | 1 thread per group per partition (3 today) | projection, two relays | MySQL (via `block(timeout)`), Kafka send |
| Kafka binder consumers | 1 per binding (2 today) | live push, order.v1 commands | order.v1 handler blocks on a command |
| R2DBC pool | 20 connections | everything above that touches MySQL | |

A first-order capacity estimate per node: commands per second are bounded by bounded-elastic threads
divided by EventSourcingDB round trip time (replay + append). Read-model freshness is bounded by the
single `conversation-read-model` thread: roughly one event per (two MySQL round trips). Fan-out is
bounded by the single `messaging-live` thread plus one Kafka consumer thread pushing into local sinks.

## 4. Tuning surface

Grouped by what they buy. "Knob" means it exists today; "change" means code.

### 4.1 Command path (message ack latency)

| Item | Kind | Effect |
|---|---|---|
| `opencqrs.command-handling.cache.ref` -> `LruInMemoryStateRebuildingCache(n)` | knob | replay only the events after the cached state; turns `PostMessage` from O(messages) to O(1) reads |
| Bound `messagesByClientId` (keep the last K ids, or ids newer than a window) | change | caps aggregate size and JSON size of the cached state; K must exceed a client's realistic retry window |
| Bounded-elastic size (`reactor.schedulers.defaultBoundedElasticSize`) | knob | more concurrent commands in flight; costs EventSourcingDB connections |
| EventSourcingDB client timeouts / connection pool | knob (esdb-client) | fail fast instead of tying up a thread for a slow store |
| Skip the command log for WebSocket posts (already the case) and purge `cqrs_command_log` by age | change | the table grows forever today |
| `AnnounceLookup` timeout and circuit breaker settings (Resilience4j) | knob | `POST /api/conversations` latency under announce-management trouble |

### 4.2 Read side (freshness of REST reads, checkpoint cost)

| Item | Kind | Effect |
|---|---|---|
| `opencqrs.event-handling.groups.<g>.life-cycle.partitions` > 1 | knob | parallel projection across conversations; per-subject order kept; checkpoint rows per partition |
| Lock-registry lifecycle controller for multi-node | knob + bean | one active copy of each group per partition across nodes; removes N-fold duplicate projection and N copies on the live topic |
| Retry policy `max-elapsed-time` and alerting on a stopped processor | knob | today a group silently stalls after 1 h of failures |
| `cqrs.readside.handler-timeout` | knob | lower for the projection (should be milliseconds), keep higher for relays |
| Checkpoint every N events or every T ms instead of per event | change (framework) | halves MySQL statements per event; widens the at-least-once replay window |
| Projection: drop `selectByPrimaryKey` before `updateOffers` on OFFER (one conditional update) | change | fewer round trips on offer messages |

### 4.3 Fan-out (delivery latency, socket safety)

| Item | Kind | Effect |
|---|---|---|
| Kafka producer `linger.ms` (0 for latency), `acks`, `compression` on `messaging-live-out-0` | knob | produce latency; the live topic can use `acks=1` because loss only costs a push clients can recover by `GET .../messages?after=` |
| Consumer `fetch.min.bytes=1`, `fetch.max.wait.ms`, `max.poll.records` on `liveEvents-in-0` | knob | consume latency |
| Partitions of `messaging.live` | knob (topic) | parallelism of the fan-out consumer (with `concurrency`) |
| `LiveSink` bounded buffer (`onBackpressureBuffer(n, drop-oldest)` or close the socket on overflow) | change | protects heap from slow clients; clients catch up over REST |
| Direct local push: the live relay also pushes to `LiveSessions` before Kafka | change | removes Kafka from the latency path for sockets on the publishing node; duplicates on that node must be suppressed by `messageId` |
| Stable per-node consumer group name (`messaging-live-${HOSTNAME}`) | knob | no dead groups after restarts; still `latest` offset |
| WebSocket idle timeout, max frame size, ping interval from the server | change | reclaim dead sockets, bound inbound frames |
| Frame handling on the socket: `flatMap` with small concurrency instead of `concatMap` | change | a slow command no longer blocks the same client's `read`/`ping` frames; ordering of the client's own posts is already guaranteed by `seq` |

### 4.4 Queries

| Item | Kind | Effect |
|---|---|---|
| Inbox: fetch participants for the page in one `IN` query | change | 21 statements -> 2 per page |
| `max-page-size`, default page 50 | knob | payload per request |
| Indexes: `announce_conversation` needs (participant lookup) coverage for `selectByParticipant`; verify with `EXPLAIN` under load | check | |

### 4.5 Subscribers

| Item | Kind | Effect |
|---|---|---|
| `spring.cloud.stream.bindings.orderEvents-in-0.consumer.concurrency`, `max-attempts`, `back-off-*` | knob | throughput and behaviour on failure |
| `spring.cloud.stream.kafka.bindings.orderEvents-in-0.consumer.enable-dlq` | knob | poisoned record moves aside instead of blocking |

### 4.6 Connections

| Item | Kind | Effect |
|---|---|---|
| R2DBC `pool.max-size` | knob | must cover processor threads x 2 + binder threads + request concurrency; 20 is tight once partitions rise |
| `pool.max-idle-time`, `validation-query` | knob | avoid stale connections after MySQL restarts |

## 5. What is measured (implemented 2026-09-29)

Package `infrastructure.metrics`; names in `MessagingMeters`. Every meter carries the tag `node`
(`management.metrics.tags.node`, from `ANNOUNCE_NODE`, default the server port). The `messaging.*` timers
publish p50/p95/p99 in the process (`MessagingMetricsConfig`), so a plain scrape of `/actuator/prometheus`
shows them; no histogram buckets, because the Prometheus client Micrometer now uses drops the quantile
samples of a meter that also has buckets. With a Prometheus server, set
`management.metrics.distribution.percentiles-histogram.messaging=true` and use `histogram_quantile`.

| Meter | Type | Tags | Where | Meaning |
|---|---|---|---|---|
| `messaging.command.latency` | timer | `command`, `outcome` (`ok`, `rejected`, `error`) | `Conversations.send` | dispatch to answer, retries included, i.e. the ack latency minus the socket hop |
| `messaging.command.retries` | counter | `command` | `Conversations.send` | re-dispatches after EventSourcingDB refused a write because another command changed the conversation first; the contention on single conversations |
| `messaging.live.latency` | timer | `record` (`message`, `status`, `bulletin`) | `LiveEventsSubscriber` | record timestamp (the event time on the command side) to the push on this node: ESDB write, processor pickup, relay, Kafka, consumer |
| `messaging.live.delivered` | counter | `record` | `LiveEventsSubscriber` | frames pushed to local sockets |
| `messaging.projection.delay` | timer | `group` | `MeteredProjection` | event written in ESDB to handled by the group on this node; the per-event lag |
| `messaging.projection.handler` | timer | `group`, `outcome` | `MeteredProjection` | the handler's own duration |
| `messaging.sessions`, `messaging.sessions.conversations`, `messaging.staff_sessions` | gauge | | `LiveSessions` | sockets on this node, conversations with a socket, staff sockets |
| `messaging.sink.buffered`, `messaging.sink.buffered.max` | gauge | | `LiveSessions` | frames waiting in the outbound buffers (sum, and the deepest socket) |
| `messaging.sink.dropped` | counter | `reason` (the `EmitResult`) | `LiveSink` | frames a socket buffer refused |

`MeteredProjection` wraps every `ReactiveProjection` bean (the read model and the two relays) through a
`BeanPostProcessor`, so the framework is untouched; moving the timer into `ReactiveProjectionAdapter` would
give every service the same numbers and is the natural follow-up. Two meters come for free: the R2DBC pool
(`r2dbc.pool.acquired|allocated|pending|idle`, from Boot's pool metrics on the reactive-mybatis pool) and the
Kafka consumer lag (`spring.cloud.stream.binder.kafka.offset` per group and topic).

Deviation from the first draft of this section: the lag is a per-event timer (`messaging.projection.delay`)
rather than a gauge of "ESDB head minus checkpoint". The gauge needs a head read of EventSourcingDB on every
scrape and grows while the service is idle; the timer is zero-cost, exact, and only silent when nothing
happens, which is fine for a load test.

### The load driver

`messaging/src/test/java/.../load/ChatLoadDriver.java` produces the load shape agreed here: N support
conversations (no announce lookup needed, so no announce-management), a member socket and a staff socket
each, every socket posting a `post` frame every `intervalMs`. It reports the driver-side ack latency (send to
`ack`) and fan-out latency (send to the `message` frame on either socket), then dumps the relevant lines of
`/actuator/prometheus`. With a node up:

```
mvn -pl messaging -DskipTests test-compile exec:java -Dexec.classpathScope=test \
    -Dexec.mainClass=org.linlinjava.announce.messaging.load.ChatLoadDriver \
    -Dload.base=http://localhost:8086 -Dload.conversations=20 -Dload.seconds=60 -Dload.intervalMs=3000
```

(`-Dmaven.test.skip=true` would skip compiling the driver.) The conversations stay in the read model; remove
them with `delete ... where subject like 'load %'` on `announce_conversation` and the participant and
message rows keyed by those ids. A rebuild of the read model brings them back from the event store.

Two defects the baseline run found, both fixed on 2026-10-01:

- **Read-your-writes gap on the socket handshake.** `ConversationWebSocketHandler` authorises against the
  read model; a client that connected right after the conversation was opened was closed with 4403 because
  the projection had not written the row yet (p95 of `messaging.projection.delay` is about 120 ms, max 770 ms
  under this load). The driver worked around it by polling `GET /api/conversations/{id}`. Fix:
  `ConversationQueries.mayAttach` looks a conversation the read model does not have up again,
  `messaging.attach.rechecks` (5) times, `recheck-interval` (200 ms) apart, before the socket is refused. Only
  the missing row is re-checked: the row and its first participants are written in one transaction, members
  never join later and staff may attach to anything, so a stranger on an existing conversation is still
  refused at once. A lag above one second (the 770 ms maximum is close) still refuses; raise the two settings
  if `messaging.projection.delay` grows, or move the check to the command side.
- **Concurrent posts in one conversation failed.** When both participants post at the same moment, the second
  write hits EventSourcingDB's precondition (409) and OpenCQRS raises `ConcurrencyException`; nothing retried,
  and the WebSocket handler answered `error` 500 `internal`. Under the baseline load 12 of 800 posts (1.5 %)
  were lost this way. Fix: `Conversations.send` re-subscribes to the dispatch on `ConcurrencyException`, at
  most `messaging.command-retry.max-retries` (3) times with a jittered back-off of 5 to 50 ms; nothing was
  written by the refused attempt, so the handler simply runs again on the new state. Every failed attempt
  means another writer got through, so up to four simultaneous writers always succeed. A writer that keeps
  losing against a continuous stream (possible: the back-off puts it behind the next post of the other
  socket) answers 409 `concurrent-modification` on the socket as on REST, and the client repeats the frame
  with the same `clientMsgId`. The retry lives in the messaging facade, not in `ReactiveCommandRouter`, to
  keep the change inside this module; moving it into the router would give `order` and `announce-management`
  the same behaviour and is a framework follow-up. Serialising the commands of one conversation inside a
  node would remove same-node races altogether and is not done.

## 6. Order of experiments

0. Retry on `ConcurrencyException` and the handshake re-check (defects found by the baseline; see
   section 5). Done 2026-10-01. Verified by `MessagingEndToEndTest`: ten rounds of buyer and seller posting
   at the same moment over two sockets plus four simultaneous REST posts, 24 of 24 acknowledged with distinct
   `seq`, `outcome=error` at zero, and `messaging.command.retries` above zero (15 re-dispatches in the run of
   2026-10-01); with
   `max-retries=0` the first round already loses a post, and with `attach.rechecks=0` the socket opened right
   after the conversation is refused. Measured under the baseline load on 2026-10-10 (section 7, rerun): 1,599
   posts in two runs, 1,599 acknowledged, `outcome=error` zero, 361 re-dispatches.
1. Instrument (section 5). Done 2026-09-29; baseline in section 7.
2. State rebuilding cache on the command side (`opencqrs.command-handling.cache.type`, `capacity`, `ref`);
   measure `messaging.command.latency` for `PostMessage` on long conversations.
3. Leader-elected processors (lock registry) before any multi-node test, or the numbers will be
   polluted by duplicate work.
4. Kafka producer/consumer latency settings on the live topic; measure `messaging.live.latency`.
5. Bounded `LiveSink` and socket timeouts; measure heap and `messaging.sink.dropped` with a stalled client.
6. Read-side partitions and R2DBC pool size together; measure `messaging.projection.delay` under load.
7. Query batching for the inbox.

Each step is small, independently revertable, and has one metric that tells whether it helped.

## 7. Baseline, 2026-09-29

One node (`ANNOUNCE_NODE=node-a`, port 8086, `mvn spring-boot:run`, no Eureka), the `docker/cqrs` stack
(EventSourcingDB, Kafka on 9094) and the live MySQL on the same 8-core host as the driver. Load: 20
conversations, 40 sockets, one post per socket every 3 s for 60 s (800 posts, about 13 posts/s). The
conversations are new, so every one is short (at most 40 events); the command-side replay cost of long
conversations is not in these numbers.

Driver side:

| | n | p50 | p95 | p99 | max |
|---|---|---|---|---|---|
| ack latency (send to `ack`) | 788 | 66 ms | 131 ms | 160 ms | 179 ms |
| fan-out latency (send to `message` on either socket) | 1576 | 69 ms | 133 ms | 163 ms | 183 ms |

Errors: 12 of 800 posts answered `error` 500 `internal`, all `ConcurrencyException` (section 5).

Service side (`/actuator/prometheus` after the run):

| Meter | p50 | p95 | p99 | count |
|---|---|---|---|---|
| `messaging.command.latency{command=PostMessage,outcome=ok}` | 65 ms | 132 ms | 166 ms | 824 |
| `messaging.live.latency{record=message}` | 48 ms | 103 ms | 120 ms | |
| `messaging.projection.delay{group=conversation-read-model}` | 84 ms | 122 ms | 155 ms | 870 |
| `messaging.projection.delay{group=messaging-live}` | 44 ms | 61 ms | 69 ms | 847 |
| `messaging.projection.handler{group=conversation-read-model}` | 41 ms | | | 870 (sum 38.5 s) |
| `messaging.projection.handler{group=messaging-live}` | 0.8 ms | | | 847 |

R2DBC pool: at most 6 connections allocated, none pending. Heap after the run about 160 MB used, 61 live
threads. `messaging.sink.buffered` stayed at 0 (no socket fell behind at this rate), `messaging.sink.dropped`
absent.

What the numbers say:

- The command path (about 65 ms median for a post) is where most of the ack latency is, and the aggregate
  has at most 40 events here; step 2 (state cache) targets exactly this and long conversations will make it
  worse before the cache is on.
- The read-model handler costs about 41 ms median per event (two table writes and the checkpoint, each a MySQL
  round trip through the pool), i.e. the `conversation-read-model` group can absorb about 25 events/s per
  partition before its lag grows without bound; the live relay costs under 1 ms. Step 6 (partitions, pool)
  and per-batch checkpoints are the levers.
- The fan-out (event time to socket push, 48 ms median) is dominated by the processor pickup and the Kafka
  hop; the driver's fan-out latency is only 3 ms above its ack latency, so the live path is not the bottleneck
  at this load.
- Ack p95 (131 ms) is twice the median: the contention is on EventSourcingDB and MySQL, both shared with the
  three processors on the same node; step 3 (one processor set per group across nodes) matters even for one
  node once a second one joins.

## 7a. Rerun with the race fixes, 2026-10-10

Same node, stack and driver settings as the baseline, one node started with
`--announce.service-token.mode=off` (the edges' token check is on by default since 2026-10-02). Two runs, 60 s
each, 20 conversations, 40 sockets. The host was not idle: a video call held about two cores throughout, and
during the first run three test suites of another worktree were running as well (load average 13 on 8 cores).
The numbers are therefore an upper bound, not a new baseline; the baseline's host condition was not recorded.

| | baseline 2026-09-29 | run 1 (loaded host) | run 2 (video call only) |
|---|---|---|---|
| posts sent / acknowledged | 800 / 788 | 799 / 799 | 800 / 800 |
| `error` frames | 12 (1.5 %) | 0 | 0 |
| `messaging.command.retries{command=PostMessage}` | n/a | 316 | 45 |
| ack latency p50 / p95 / p99 | 66 / 131 / 160 ms | 1144 / 4152 / 5313 ms | 142 / 350 / 518 ms |
| `messaging.live.latency{record=message}` p50 / p95 | 48 / 103 ms | 703 / 1743 ms | 111 / 300 ms |
| `messaging.projection.handler{group=conversation-read-model}` p50 | 41 ms | 103 ms | 73 ms |
| `messaging.projection.delay{group=conversation-read-model}` p50 / max | 84 / 155 ms | 23.1 / 35.8 s | 6.8 / 8.7 s |
| R2DBC pool max allocated / pending | 6 / 0 | 5 / 0 | 5 / 0 |

What the rerun says:

- **The defect is closed.** No post was lost in either run, and the read model holds every one of the 1,599
  messages. Under the loaded host 316 of 799 posts collided at least once, because a command that takes a
  second overlaps the other socket's next post far more often; all of them were absorbed by the retries.
  Nothing hit the retry bound.
- **The retry count is a load signal.** 45 collisions in run 2 against 12 at the baseline, with the same post
  rate, is the longer command path (142 ms median instead of 66 ms) widening the window in which two posts
  read the same state. Watch `messaging.command.retries` together with `messaging.command.latency`.
- **The read-model group runs at its limit on this host.** The handler costs 73 ms per event here (41 ms at
  the baseline), so one partition absorbs about 14 events/s and the load is 13 posts/s plus the conversation
  events: the lag grows to about 7 s during the run and clears within seconds after it. This is the
  capacity ceiling section 4.2 describes, reached earlier because the host is slower, not a change in the
  code; steps 3 and 6 of section 6 are the remedy. Re-measure on an idle host before reading it as a
  regression.
- **Fan-out is unaffected by the fixes.** The live relay still handles an event in under a millisecond and
  the socket push follows the ack by a few milliseconds, as at the baseline.

Housekeeping for a load run: the three checkpoints in `cqrs_readside_progress` were set to the local
store's last `/conversation` event id before the node started (they held ids of the test's container, see
`doc/messaging-context.md` section 7), and the 40 `load %` conversations with their participants and
messages were deleted from the read model afterwards.
