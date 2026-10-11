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
| `ConversationQueries` | inbox, one conversation, message page, support queue; always the MySQL read model; `mayAttach` for the socket handshake looks an unknown conversation up again (`messaging.attach.*`) | A page (inbox, support queue) is two statements whatever its size: the page, then the participants of the whole page in one `IN` query (step 7, 2026-10-11; before it, one participants query per row, 21 statements for a page of 20); every read is timed as `messaging.query.latency{query}` |
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

Since 2026-10-10 `opencqrs.command-handling.cache.type=in_memory` (`capacity` 1000) gives the router an
`LruInMemoryStateRebuildingCache` keyed by subject, state class and sourcing mode. An entry holds the rebuilt
state and the id of the last event it saw; on the next command the router reads only the events after that id
(`LowerBoundExclusive`), applies them and handles. A stale entry on one node is therefore harmless: another
node's writes are read as the tail. Before this the default was `none`, so every post to a conversation with
N messages replayed N events from EventSourcingDB; section 7b shows what that cost. The entry of a
conversation grows by about 150 bytes per message (`messagesByClientId`), so the capacity bounds the memory at
roughly 150 KB per 1,000-message conversation. `MessagingEndToEndTest` pins the cache type.

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
- `life-cycle.partitions` is 4 for `conversation-read-model` since 2026-10-10 (section 7c) and 1 for the two
  relays. Sequence resolution is `per_second_level_subject`, meaning ordering is only required per
  `/conversation/{id}`, so the four threads project four conversations at once without breaking order; the
  checkpoint table holds one row per partition. Changing the count is an administrative step with every node
  stopped; the framework seeds new partitions from the lowest existing checkpoint
  (`cqrs.readside.progress.seed-partitions`).
- `R2dbcProgressTracker.proceed` writes the checkpoint after every event: read-model write and
  checkpoint are two round trips to MySQL per event per group, so three groups cost roughly four
  MySQL statements plus two Kafka sends per `MessagePosted`.
- `life-cycle.controller=leader_election` since 2026-10-10 (section 7c): each group and partition runs on
  the one node that holds its lease in `cqrs_readside_lock` (`cqrs.readside.leader-election`, the R2DBC lock
  registry of `cqrs-reactive`), renewed every 500 ms, taken over by another node 10 s after the holder stops
  renewing. With the previous `application_context` controller every node ran every processor: the work was
  done N times and the live topic carried N copies of each record, so every socket received every message N
  times (measured with two nodes in section 7c). The first node up holds every lease; a second node is a
  standby that takes over within the lease, not a load share.
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
thing answered locally. `messaging.live.transport` measures the Kafka leg alone (record timestamp to
receipt), `messaging.live.latency` the whole path (section 7d has the split).

Bulletins skip the event store: `StreamBridgeLiveFeed` publishes `BulletinLive` directly on the same
binding, from a bounded-elastic thread.

The live binding is configured for latency (tuning step 4, 2026-10-11): producer `linger.ms=0`, `acks=1`,
idempotence off, no compression; consumer `fetch.min.bytes=1`, `fetch.max.wait.ms=100`,
`max.poll.records=100`. A lost live record costs one push that the client recovers over REST, so the
durability of `acks=all` buys nothing here; the public topic `messaging.v1` keeps the durable defaults.
The consumer group is named after the node (`messaging-live-${ANNOUNCE_NODE}`, host and port as the
fallback) with `reset-offsets=true`, so a restart leaves no dead group behind and still starts at the
latest offset: a node that restarts holds no sockets, so the records published while it was down are of
no use to it.

Every socket is run by `SocketLifecycle` (tuning step 5, 2026-10-11): a protocol ping every
`messaging.socket.ping-interval` (30 s); close 4409 when nothing arrived for `idle-timeout` (75 s); the
`LiveSink` behind the socket holds at most `buffer-frames` (256) frames and, once it overflowed, delivers
what it holds and closes the socket with 4408 (slow consumer). The idle check is a watchdog, not a
`timeout` operator on the inbound: cancelling the inbound makes Reactor Netty send an empty close frame of
its own, and the client would see 1005 instead of the reason. A connection that stopped taking bytes
altogether cannot even receive a close frame; Netty's `WriteTimeoutHandler` on every connection of the
server (`write-timeout`, 30 s) ends it.

### 2.7 Public events and subscriptions

- `messaging.v1` out: `ConversationOpenedV1`, `OfferAcceptedV1` (consumed by the order module).
- `order.v1` in: `OrderAgreedV1`, `OrderPaidV1`, `OrderShippedV1`, `OrderSettledV1` become SYSTEM
  messages. Each record is one command on the binder thread, awaited with
  `cqrs.messaging.handler-timeout` (30 s); the consumer group `messaging` has the binder default
  concurrency of 1 and no DLQ configured, so a poisoned record blocks the partition after retries.

### 2.8 Persistence and connections

| Resource | Client | Used by | Setting today |
|---|---|---|---|
| MySQL `announce_reactive_db` | R2DBC pool from reactive-mybatis-support (`mybatis-r2dbc-spring` 3.1.3, set in `db-reactive`; 3.0.6 generated a ByteBuddy class per statement, section 7f), shared with Spring Data R2DBC | read model, checkpoints, command log, read markers, bulletins | `initial-size 2`, `max-size 40` |
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
| OpenCQRS processors | 1 thread per group per partition (6 today: 4 + 1 + 1), on the node holding the lease | projection, two relays | MySQL (via `block(timeout)`), Kafka send |
| Leader initiators | 1 thread per processor per node (6) | lease renewal or polling, 2 statements per heartbeat | MySQL |
| Kafka binder consumers | 1 per binding (2 today) | live push, order.v1 commands | order.v1 handler blocks on a command |
| R2DBC pool | 40 connections | everything above that touches MySQL | |

A first-order capacity estimate per node: commands per second are bounded by bounded-elastic threads
divided by EventSourcingDB round trip time (replay + append). Read-model freshness is bounded by the
`conversation-read-model` threads: roughly one event per (two MySQL round trips) per partition, about 23
events/s per partition on this host, so about 90 events/s with four. Fan-out is bounded by the single
`messaging-live` thread plus one Kafka consumer thread pushing into local sinks.

## 4. Tuning surface

Grouped by what they buy. "Knob" means it exists today; "change" means code.

### 4.1 Command path (message ack latency)

| Item | Kind | Effect |
|---|---|---|
| `opencqrs.command-handling.cache.type=in_memory`, `capacity` (on since 2026-10-10) | knob | replay only the events after the cached state; turns `PostMessage` from O(messages) to O(1) reads (section 7b) |
| Bound `messagesByClientId` (keep the last K ids, or ids newer than a window) | change | caps aggregate size and JSON size of the cached state; K must exceed a client's realistic retry window |
| Bounded-elastic size (`reactor.schedulers.defaultBoundedElasticSize`) | knob | more concurrent commands in flight; costs EventSourcingDB connections |
| EventSourcingDB client timeouts / connection pool | knob (esdb-client) | fail fast instead of tying up a thread for a slow store |
| Skip the command log for WebSocket posts (already the case) and purge `cqrs_command_log` by age | change | the table grows forever today |
| `AnnounceLookup` timeout and circuit breaker settings (Resilience4j) | knob | `POST /api/conversations` latency under announce-management trouble |

### 4.2 Read side (freshness of REST reads, checkpoint cost)

| Item | Kind | Effect |
|---|---|---|
| `opencqrs.event-handling.groups.<g>.life-cycle.partitions` > 1 (4 for the read model since 2026-10-10) | knob | parallel projection across conversations; per-subject order kept; checkpoint rows per partition, seeded by the framework |
| Leader-election lifecycle controller with the R2DBC lock registry (on since 2026-10-10) | knob + bean | one active copy of each group per partition across nodes; removes N-fold duplicate projection and N copies on the live topic; `ttl` bounds the failover gap |
| Retry policy `max-elapsed-time` and alerting on a stopped processor | knob | today a group silently stalls after 1 h of failures |
| `cqrs.readside.handler-timeout` | knob | lower for the projection (should be milliseconds), keep higher for relays |
| Checkpoint every N events or every T ms instead of per event | change (framework) | halves MySQL statements per event; widens the at-least-once replay window |
| Projection: drop `selectByPrimaryKey` before `updateOffers` on OFFER (one conditional update) | change | fewer round trips on offer messages |

### 4.3 Fan-out (delivery latency, socket safety)

| Item | Kind | Effect |
|---|---|---|
| Kafka producer `linger.ms` (0 for latency), `acks`, `compression` on `messaging-live-out-0` (set 2026-10-11, section 7d) | knob | produce latency; the live topic uses `acks=1` because loss only costs a push clients can recover by `GET .../messages?after=` |
| Consumer `fetch.min.bytes=1`, `fetch.max.wait.ms`, `max.poll.records` on `liveEvents-in-0` (set 2026-10-11) | knob | consume latency |
| Partitions of `messaging.live` | knob (topic) | parallelism of the fan-out consumer (with `concurrency`) |
| `LiveSink` bounded buffer, close 4408 on overflow (done 2026-10-11, section 7d) | change | protects heap from slow clients; clients catch up over REST |
| Direct local push: the live relay also pushes to `LiveSessions` before Kafka | change | removes Kafka from the latency path for sockets on the publishing node; duplicates on that node must be suppressed by `messageId`; section 7d shows the Kafka leg is a few milliseconds, so the gain would be small |
| Stable per-node consumer group name with `reset-offsets` (done 2026-10-11) | knob | no dead groups after restarts; still `latest` offset |
| Server ping, idle close 4409, write timeout (done 2026-10-11, section 7d); max inbound frame size stays Reactor Netty's 64 KB | change | reclaim dead sockets, bound inbound frames |
| Frame handling on the socket: `flatMap` with small concurrency instead of `concatMap` | change | a slow command no longer blocks the same client's `read`/`ping` frames; ordering of the client's own posts is already guaranteed by `seq` |

### 4.4 Queries

| Item | Kind | Effect |
|---|---|---|
| Inbox: fetch participants for the page in one `IN` query (done 2026-10-11, section 7e) | change | 21 statements -> 2 per page; a page of 20 from 1.5 s to 0.17 s, a page of 50 from 3.4 s to 0.18 s under four concurrent readers |
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
| R2DBC `pool.max-size` (40 since 2026-10-10) | knob | must cover processor threads x 2 + binder threads + lock heartbeats + request concurrency; 20 was tight once the read model was partitioned |
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
| `messaging.live.transport` | timer | `record` | `LiveEventsSubscriber` | the Kafka leg alone: record timestamp (the relay's send) to receipt by this node's consumer |
| `messaging.live.delivered` | counter | `record` | `LiveEventsSubscriber` | frames pushed to local sockets |
| `messaging.query.latency` | timer | `query` (`inbox`, `by_status`, `conversation`, `messages`), `outcome` | `ConversationQueries` | one read over the read model, from the subscription to the complete answer; the cost of a REST read minus the HTTP hop |
| `messaging.projection.delay` | timer | `group` | `MeteredProjection` | event written in ESDB to handled by the group on this node; the per-event lag |
| `messaging.projection.handler` | timer | `group`, `outcome` | `MeteredProjection` | the handler's own duration |
| `messaging.sessions`, `messaging.sessions.conversations`, `messaging.staff_sessions` | gauge | | `LiveSessions` | sockets on this node, conversations with a socket, staff sockets |
| `messaging.sink.buffered`, `messaging.sink.buffered.max` | gauge | | `LiveSessions` | frames waiting in the outbound buffers (sum, and the deepest socket) |
| `messaging.sink.dropped` | counter | `reason` (the `EmitResult`) | `LiveSink` | frames a socket buffer refused (`FAIL_OVERFLOW`: the socket was closed as a slow consumer) |

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
   measure `messaging.command.latency` for `PostMessage` on long conversations. Done 2026-10-10: `in_memory`,
   capacity 1000, in both profiles; A/B with 200-message conversations in section 7b (post latency p50 3.5 s
   and half the posts lost without the cache, 78 ms and nothing lost with it).
3. Leader-elected processors (lock registry) before any multi-node test, or the numbers will be
   polluted by duplicate work. Done 2026-10-10: `R2dbcLockRegistry` in `cqrs-reactive`,
   `life-cycle.controller=leader_election`; two-node A/B and a crash of the leader in section 7c.
4. Kafka producer/consumer latency settings on the live topic; measure `messaging.live.latency`. Done
   2026-10-11: the Kafka leg measured on its own (`messaging.live.transport`), latency settings and a stable
   consumer group on the live binding; section 7d.
5. Bounded `LiveSink` and socket timeouts; measure heap and `messaging.sink.dropped` with a stalled client.
   Done 2026-10-11: `messaging.socket.*` (256 frames, ping 30 s, idle 75 s, write timeout 30 s), close 4408 /
   4409; section 7d.
6. Read-side partitions and R2DBC pool size together; measure `messaging.projection.delay` under load.
   Done 2026-10-10: 4 partitions for `conversation-read-model`, pool 40; lag p50 from 32 s to 0.08 s in
   section 7c.
7. Query batching for the inbox. Done 2026-10-11: one `IN` query for the participants of a page, `messaging.query.latency`
   timer; section 7e, where the per-statement cost of the reactive MySQL path is the finding that remains.
8. Command-side throughput (found by the burst of step 4, section 7d): at 40 posts/s with 40 commands in
   flight a post takes about a second inside the facade and the acks queue up; find what serialises the
   command path (the EventSourcingDB client's connection pool, the store itself, the bounded-elastic hop)
   and measure `messaging.command.latency` against the number of commands in flight.
9. The per-statement cost of the reactive MySQL path (found by step 7, section 7e). Done 2026-10-11:
   reactive-mybatis-support 3.0.6 generated a ByteBuddy class on every statement; `mybatis-r2dbc-spring`
   3.1.3 in db-reactive, a mapper call from 19.5 to 8.7 ms alone and from 39.5 to 7.8 ms with four callers;
   section 7f.

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

## 7b. State cache A/B on long conversations, 2026-10-10

The baseline and the rerun post into new conversations, so the replay cost of a long history was invisible.
`ChatLoadDriver` now takes `-Dload.prefill=N`: before the sockets start it posts N messages into every
conversation over REST (conversations in parallel, each one sequentially). Two runs back to back on the same
host, same node, 20 conversations, 40 sockets, 60 s, one post per socket every 3 s, prefill 200, so every post
of the run works on a history of 200 to 240 events. Run A started the node with
`--opencqrs.command-handling.cache.type=none`, run B with the new default (`in_memory`, capacity 1000). The
host had just been rebooted; the prefill itself is the main load (load average 7 to 9 during both runs).

| | A: cache off | B: cache on |
|---|---|---|
| prefill of 4,000 messages | 323 s | 211 s |
| posts sent / acknowledged within the run | 798 / 369 | 800 / 800 |
| `error` frames | 79 | 0 |
| `messaging.command.retries{command=PostMessage}` | 271 | 37 |
| ack latency p50 / p95 / p99 / max | 8.7 / 15.8 / 21.8 / 23.4 s | 74 / 295 / 378 / 554 ms |
| `messaging.command.latency{PostMessage,ok}` p50 / p95 | 3.5 / 5.0 s | 78 / 333 ms |
| `messaging.command.latency{PostMessage,error}` p50 | 14.0 s | none |
| `messaging.live.latency{record=message}` p50 / p95 | 3.4 / 4.7 s | 57 / 208 ms |
| `messaging.projection.handler{conversation-read-model}` p50 | 82 ms | 45 ms |
| `messaging.projection.delay{conversation-read-model}` p50 | 0.15 s | 54 s (see below) |
| heap, old generation, after the run | 187 MB | 177 MB |

What the A/B says:

- **Without the cache a 200-message conversation is unusable at this load.** Each post replayed about 220
  events (3.5 s median on this host, with 40 sockets competing for the bounded-elastic threads and the
  event store's HTTP connections). Half the posts were still unanswered when the run ended, and 79 posts
  failed outright: a command that takes 3.5 s overlaps the other socket's next post almost always, the four
  attempts add up to the 14 s seen on the `error` timer, and the retry bound is reached. The retry of
  section 7a cannot rescue a command path this slow; it was never meant to.
- **With the cache the long history costs nothing.** 78 ms median per post is the figure of a fresh
  conversation (section 7a, run 2: 142 ms on a busier host; baseline 65 ms), every post acknowledged, 37
  collisions absorbed. The cache does not change the memory picture: the old generation ended 10 MB lower
  than in run A, with 20 cached conversations of 240 events each.
- **The prefill shows the same thing from the REST side**: 4,000 posts in 211 s instead of 323 s, with the
  remaining time spent in the event store writes and the read-model handler, not in replay.
- **The read-model group is the next limit.** In run B the handler was back at 45 ms per event (82 ms in run
  A, where the replaying commands competed for the same MySQL connections and CPU), but 4,800 events arrived
  in about four and a half minutes against a capacity of about 22 events/s per partition, and the lag reached
  54 s before clearing after the run. That is steps 3 and 6 of section 6 (one processor set per group across
  nodes, more partitions and a larger pool); the command side is no longer the bottleneck.

Housekeeping as in section 7a: checkpoints set to the store's head before the first node started (they held
ids of the test container after the suite ran), the node restarted between the runs, the 40 `load %`
conversations and their 9,209 messages deleted from the read model afterwards.

## 7c. Leader election and read-model partitions, 2026-10-10

Steps 3 and 6 of section 6, implemented in `cqrs-reactive` (`R2dbcLockRegistry`, partition seeding in
`R2dbcProgressTracker`, `cqrs.readside.leader-election.*`) and switched on in this service
(`life-cycle.controller=leader_election`, `conversation-read-model` with 4 partitions, pool 40). Four runs with
the load of section 7b (20 support conversations prefilled with 200 messages each over REST, then 40 sockets
posting every 3 s for 60 s: 4,800 `MessagePosted` per run, 4,840 events for the read model). The shared dev
EventSourcingDB had stopped answering streaming reads that afternoon (`ping` fine, every `read-events` hung;
it needs a restart of its container), so the runs used a fresh `thenativeweb/eventsourcingdb:1.2.0` container
on port 3001 with a temporary data directory, the dev Kafka and the live `announce_reactive_db`. Load average
2 to 5 during the runs. The nodes were started with `--esdb.server.uri=http://localhost:3001`, service token
off, and the three groups' checkpoints set to the store's head before every run.

**Step 6, one node, partitions and pool (runs 1 and 2).** Run 1 started the node with
`--opencqrs.event-handling.groups.conversation-read-model.life-cycle.partitions=1 --spring.r2dbc.mybatis.pool.max-size=20`
(the previous settings), run 2 with the new defaults (4 partitions, pool 40).

| | run 1: 1 partition, pool 20 | run 2: 4 partitions, pool 40 |
|---|---|---|
| prefill of 4,000 messages over REST | 169 s | 192 s |
| posts sent / acknowledged / `error` frames | 800 / 800 / 0 | 800 / 800 / 0 |
| ack latency p50 / p95 / max | 61 / 207 / 504 ms | 48 / 127 / 179 ms |
| `messaging.command.latency{PostMessage,ok}` p50 / p95 | 64 / 234 ms | 47 / 129 ms |
| `messaging.projection.handler{conversation-read-model}` p50, per event | 39 ms | 41 ms |
| `messaging.projection.delay{conversation-read-model}` p50 / p95 / max | 31.7 / 42.4 / 41.8 s | 0.08 / 0.11 / 0.31 s |
| read model caught up after the driver ended | 10 s later | already at the head |
| heap, old generation, after the run | 188 MB | 255 MB |

The single thread handled 4,840 events in 209 s of handler time: 23 events/s against an arrival rate of
about 24/s during the prefill, so the lag grew for the whole prefill and peaked at 42 s. With four partitions
the handler cost per event is unchanged (41 ms: one transaction with two or three statements plus the
checkpoint) but four conversations are projected at once, and the lag never exceeded a third of a second. The
post latency improved as well (p95 from 207 to 127 ms), because the pool no longer queued the commands'
reads behind the projection; `r2dbc_pool_pending_connections` stayed at 0 in both runs, so 20 was tight rather
than exhausted. The partitions all advance their checkpoint past the events they skip, so an idle group shows
four rows at the head.

**Step 3, two nodes (runs 3 and 4).** Both nodes on this host (8086 `node-a`, 8087 `node-b`), 4 partitions,
pool 40, driver sockets on one node. Run 3 started both with
`--opencqrs.event-handling.standard.life-cycle.controller=application_context` (the previous controller), run
4 with the new default (leader election). In run 4 node-a was started first and took the six leases; node-b
got none and stayed idle; the sockets were on node-b, and node-a was killed with SIGKILL 20 s into the socket
phase, so its leases expired instead of being released.

| | run 3: every node runs every processor | run 4: leader election, leader killed |
|---|---|---|
| posts sent / acknowledged / `error` frames | 800 / 800 / 0 | 799 / 799 / 0 |
| fan-out frames received by the sockets (expected) | 3,200 (1,600): every message twice | 1,598 (1,598): every message once |
| `messaging.projection.handler{conversation-read-model}` count, node-a + node-b | 4,840 + 4,840 = 9,680 for 4,840 events | 4,280 (until the kill) + 557 = 4,837 for 4,839 events |
| `messaging.live.latency{message}` count per node (records on `messaging.live`) | 9,600 = 2 x 4,800 | 4,799 |
| ack latency p50 / p95 | 89 / 222 ms | 101 / 246 ms |
| fan-out latency p50 / p95 / max | 93 / 229 / 374 ms | 137 ms / 8.0 s / 9.7 s |
| `messaging.projection.delay{conversation-read-model}` p50 / p95 / max on the live node | 0.13 / 0.18 / 0.48 s | 0.10 / 9.1 / 9.5 s on node-b |
| leases after the run | not used | all six on node-b |

Run 3 is the duplication the old controller caused: each node projected all 4,840 events and relayed all
4,800 messages, the live topic carried two copies of every record, and every socket received every message
twice (the driver's 3,200 fan-out frames for 1,600 expected). The read model stayed correct because the
projection is an idempotent upsert, and MySQL simply did the work twice. Run 4 with leader election: node-b did
nothing while node-a held the leases (0 handler calls, 0 live records produced by it), the live topic carried
one copy, every socket received every message once. The kill at 16:27:02.75 left six leases with 10 s to live;
node-b was granted all of them between 16:27:12.50 and 16:27:12.81, 9.8 to 10.1 s after the kill, and started
the processors from the shared checkpoints: 4,280 + 557 handler calls for 4,839 events (the two events in
flight at the kill were scraped from node-a just before it died and were handled again by node-b, as
at-least-once allows). During those 10 s the command side kept acknowledging (799 of 799) and the read model
and the live push stood still: that is the 9.1 s p95 of the projection delay and the 8.0 s p95 of the fan-out
latency on the sockets, all delivered once the relay resumed, nothing lost. The lease is therefore the knob
for the failover gap; 10 s is conservative for a 500 ms heartbeat.

What this does not give: load sharing between nodes. Leases are first come first served, so one node runs
every processor until it stops. That is fine for this service (the projection needs 4 x 41 ms per 4 events,
far below one node's capacity); spreading groups over nodes would need the initiator's `yield` or a lock
registry that refuses a node more than its share, neither of which is wired.

Housekeeping: the 80 `load %` conversations and their 19,199 messages deleted from the read model, the three
groups' checkpoints and the lock rows deleted (they held ids of the temporary store; set them to the dev
store's head before the next dev node start, as section 7a says), the temporary store removed, nothing
pushed to `messaging.v1` that anyone consumed (no other service was running).

## 7d. Live topic latency settings and socket policies, 2026-10-11

One node (`ANNOUNCE_NODE=node-a`, port 8098), a throwaway EventSourcingDB started empty before every run
(the development store on port 3000 still hangs on streaming reads), the development Kafka on 9094 and the
live MySQL, all on the same host as the driver; the host was otherwise idle. Load as in section 7: 20
support conversations, 40 sockets, 60 s.

### Step 4: where the fan-out latency is

A = the binder defaults forced back on the live binding (`acks=all`, idempotent producer,
`fetch.max.wait.ms=500`, `max.poll.records=500`); B = the settings of section 2.6. The new timer
`messaging.live.transport` is the Kafka leg alone (record timestamp at the relay's send to receipt by the
consumer); `messaging.projection.delay{messaging-live}` is the store's pick-up (event written to the relay's
handler); `messaging.live.latency` is the whole path from the event time on the command side.

| Standard load, 800 posts (13 posts/s) | A: defaults | B: tuned |
|---|---|---|
| posts acknowledged / errors | 800 / 0 | 800 / 0 |
| ack latency p50 / p95 / max (driver) | 52 / 117 / 177 ms | 63 / 212 / 475 ms |
| `messaging.live.latency` p50 / p95 / p99 | 43 / 95 / 142 ms | 47 / 167 / 184 ms |
| relay pick-up p50 / p95 / p99 | 37 / 51 / 62 ms | 39 / 56 / 62 ms |
| `messaging.live.transport` p50 / p95 / p99 | 2.0 / 3.0 / 7.0 ms | 2.0 / 4.0 / 6.0 ms |

The Kafka leg is 2 ms at the median and under 10 ms at p99 on both sides; the store's observe stream
delivers the event to the relay about 37 ms after the write, and that is where the fan-out latency is. The
driver-side difference between A and B (p95 117 vs 212 ms) is run-to-run noise on a shared host: the
transport numbers are identical, and with one broker and replication 1 `acks=1` and `acks=all` wait for
the same thing. The settings are for the production shape (replicas on other hosts); the stable consumer
group (`messaging-live-${ANNOUNCE_NODE}`, `reset-offsets=true`) is the operational gain: no dead groups after
restarts.

| Burst, one post per socket per second (40 posts/s), 2,400 posts | A: defaults | B: tuned |
|---|---|---|
| posts acknowledged / errors | 1,477 / 134 | 1,446 / 111 |
| ack latency p50 / p95 / max (driver) | 12.0 / 24.8 / 32.0 s | 13.1 / 26.1 / 34.4 s |
| `messaging.command.latency{PostMessage}` p50 | 0.97 s | 1.01 s |
| `messaging.live.latency` p50 / p95 / p99 | 0.87 / 0.97 / 1.04 s | 0.84 / 0.97 / 1.04 s |
| relay pick-up p50 / p95 / p99 | 45 / 60 / 70 ms | 45 / 64 / 79 ms |
| `messaging.live.transport` p50 / p95 / p99 | 2.9 / 5.2 / 9.4 ms | 3.0 / 5.2 / 8.1 ms |

The burst is a finding of its own, not a Kafka one: at 40 posts/s with 40 commands in flight one node's
command path saturates, a post takes about a second inside the facade, the driver's open loop keeps
sending, the acks queue up to tens of seconds and about 5 % of the posts end in an error (retries exhausted
or the driver's timeout). The relay and Kafka are untouched (pick-up 45 ms, transport 3 ms); the 0.87 s of
live latency is the time the event waited to be written, since the event time is taken before the write.
A first attempt at ten times the standard rate (133 posts/s) had acknowledged 1,329 of 7,994. The same 40
sockets at 13 posts/s had every post acknowledged in 52 ms. This is step 8 of section 6.

Two invalid runs on the way, recorded so they are not repeated: clearing the checkpoints of a node that
points at a store which is no longer empty makes the processors replay the whole store onto the read model
and the live topic (a "tuned" run showed 80 s of live latency for that reason: every run now gets a fresh
store); and a burst at 300 ms per socket measures the command side's queue, nothing else.

### Step 5: sockets that do not read

What the end-to-end suite established first (test profile: bound 64 frames, ping 1 s, idle 4 s, write
timeout 10 s): a client that reads nothing while 4,000 frames of 4 KB are pushed into its session accepts
864 of them (what the loopback connection's buffers, Netty's outbound buffer and the 64-frame queue hold
together), the queue never exceeds 64, the other 3,136 are refused and counted, and when the client reads
again it receives exactly those 864 and then close 4408 with the reason. The same client pushed 60 KB
frames never receives the close frame, because the connection is full; the write timeout ends the
connection and the session is detached 10 s after the first stuck write, with nothing left queued. A client
that sees the pings and does not answer them is closed 4409 after the idle timeout while a client beside it
that answers them stays open and posts. Two lessons from the first attempt: the idle close must not be a
`timeout` operator on the inbound (a cancelled inbound makes Reactor Netty send an empty close frame of its
own and the client sees 1005), and counts must come from the sink itself (`accepted()`, `emitted()`), not
from differences of the drop counter, which gave three different answers for the same 864 delivered frames.

`StalledSocketDriver` against the running node (production settings: 256 frames, ping 30 s, idle 75 s,
write timeout 30 s; and the same node with `buffer-frames=0`, `write-timeout=0`, `idle-timeout=0`, the
state before this step): eleven staff dashboard sockets, one that reads and ten whose client never reads
nor answers a ping, then 10,000 bulletins of 1,000 characters over REST at a concurrency of 16 (the
bulletin limit is 1,023 characters). Every bulletin goes through the live topic to every staff socket:
110,000 frames of about 1.1 KB, 100,000 of them for sockets that will never take them.

| | unbounded (before) | bounded (now) |
|---|---|---|
| bulletins posted | 9,998 in 163 s | 9,998 in 163 s |
| frames queued at the end (`sink.buffered`, deepest socket) | 75,782 (7,615); unchanged a minute later | 0 (never above 256 per socket) |
| frames refused (`sink.dropped{FAIL_OVERFLOW}`) | 0 | 22,633 |
| retained for the ten stalled sockets | about 80 MB of frame strings, growing with every bulletin, never released | nothing |
| staff sockets on the node after the storm | 11, the stalled ones stay until TCP gives up | 1 (the reader); the ten stalled ones detached about 75 s after connecting, by the idle timeout since they answer no ping (the write timeout would have followed within seconds) |
| the reading socket | received every bulletin | received every bulletin |

The heap figure of the scrape is not quoted: without a forced collection it measures garbage, and both
nodes grew it to about 600 MB during the burst. The retained memory is the frames the sinks hold, and that
is 0 against 80 MB.

Housekeeping: the `load %` conversations and their messages were deleted after each run and the `load %`
bulletins after the stalled runs; the three groups' checkpoints and the lock rows are deleted (set partition
0 to the development store's head before the next development start, section 7c); the throwaway store was
removed. The development EventSourcingDB on port 3000 still needs `docker restart announce_esdb`.

## 7e. Inbox query batching, 2026-10-11

Same set-up as section 7d (one node on 8098, a fresh throwaway store, the live MySQL 8.0.34 on the same host).
`InboxLoadDriver`: one member who takes part in 50 support conversations, one staff member who joined them
all, then four clients fetching in turn the member's inbox, the staff member's inbox and the staff queue
(`status=OPEN`) with pages of 20 and 50, for 60 s. A = the code before this step (one participants query
per row: 21 statements for a page of 20, 51 for a page of 50); B = one `IN` query for the participants of
the page (2 statements whatever the size). Latency is the driver's, request to parsed answer.

| | A: one query per row | B: one query per page |
|---|---|---|
| requests answered in 60 s (four clients) | 83 = 1.4 requests/s | 1,207 = 20.1 requests/s |
| member inbox, page of 20: p50 / p95 / max | 1,497 / 1,862 / 1,862 ms | 165 / 222 / 320 ms |
| member inbox, page of 50 | 3,449 / 5,054 / 5,054 ms | 182 / 257 / 386 ms |
| staff inbox, page of 20 | 1,768 / 2,224 / 2,224 ms | 167 / 225 / 299 ms |
| staff inbox, page of 50 | 4,311 / 5,053 / 5,053 ms | 182 / 254 / 414 ms |
| staff queue, page of 20 | 1,755 / 2,124 / 2,124 ms | 164 / 231 / 318 ms |
| staff queue, page of 50 | 3,518 / 4,529 / 4,529 ms | 180 / 244 / 395 ms |
| `messaging.query.latency{inbox}` p50 / p95 / p99 (server side) | not measured before this step | 180 / 239 / 315 ms |
| R2DBC connections allocated at the end | 13 | 9 |

The page size no longer matters (a page of 50 costs 10 % more than a page of 20, not twice), and a page
is nine times faster at the median; one of about 1,200 requests in each run waited 30 s on the driver's
side while the server had answered it within 0.4 s (the server-side maximum), a client artefact that is
not counted.

What the run also shows: MySQL executes each of the statements in well under a millisecond (`EXPLAIN` on
the loaded rows: the inbox join reads the participant by `uk_participant`, the `IN` query by the same key,
the queue page by `idx_conv_status_last`; profiling gives 0.3 to 0.7 ms per statement), yet two statements
cost the service 165 ms with four clients, and the two statements of `GET /api/conversations/{id}` cost
75 ms with the node otherwise idle. The cost is per statement, in the reactive MySQL path of the service
(reactive-mybatis-support over r2dbc-mysql, through the pool), not in the database and not in the SQL. It
is the same cost that makes the read-model projection spend 40 ms per event (section 7, handler duration)
and it bounds every read and every projection of this service.

Three 30 s probes with one client to locate it (page of 20 / page of 50, driver p50, and p95 in brackets):

| Node | member inbox 20 | member inbox 50 | requests/s |
|---|---|---|---|
| as configured (r2dbc-mysql with TLS, its default on a MySQL that offers it) | 58 (269) ms | 69 (202) ms | 11.3 |
| `sslMode=DISABLED` | 59 (78) ms | 71 (95) ms | 14.7 |
| `tcpNoDelay=true` | 95 (128) ms | 120 (176) ms | 8.8 |

With one client a page costs about 60 ms, 30 ms per statement, and it is neither TLS (same median
without it; the tail is tighter, and the TLS setting stays as it is since the development host is not the
production shape) nor a Nagle or delayed-ACK stall (the 40 ms signature does not appear, and forcing
`TCP_NODELAY` does not help; that run was slower across the board, host noise). The cost is in the
service's own path from the mapper call to the mapped row: reactive-mybatis-support's statement execution
and result mapping over the pooled connection. Finding where those 30 ms go (a thread hop per statement,
a connection acquire with validation, the result mapping, logging) is step 9 of section 6; it would pay on
every read and on every event the projections handle.

## 7f. The per-statement cost: a class generated per statement, 2026-10-11

Step 7 left the question of where 30 ms per statement go when MySQL executes the statement in under a
millisecond. The `announce` user may not read the performance schema, so the measurement is client-side
and layered: `StatementCostProbeTest` (runs only with `-Dprobe=true`, under the test profile with the
containers of the end-to-end suite and the live read model) runs the same point query on one conversation
row 400 times in sequence at each layer of the path and reports the latency of one call as the caller
sees it, p50 (p95). Same host, development stack up.

| Layer | 3.0.6 | 3.1.3 |
|---|---|---|
| r2dbc-mysql driver, one connection, no pool | 1.8 (3.2) ms | 2.7 (9.0) ms |
| pooled connection factory: acquire, statement, release | 2.1 (3.6) ms | 3.5 (13.7) ms |
| mapper `selectByPrimaryKey` | 19.5 (39.0) ms | 8.7 (28.4) ms |
| mapper, 400 calls on one transaction's connection, mean per call | 13.5 ms | 3.6 ms |
| mapper `selectByExample`, two participant rows | 13.9 (20.9) ms | 5.1 (13.3) ms |
| pooled factory, bare `select 1` | 1.0 (4.5) ms | 1.3 (5.8) ms |
| mapper, four callers at once | 39.5 (81.4) ms | 7.8 (23.8) ms |
| pooled factory, four callers at once | 1.6 (5.5) ms | 2.1 (9.6) ms |

(The 3.1.3 column was taken while other builds ran on the host, which the driver and pool rows show; the
mapper rows fell regardless. A quieter rerun is in the report.)

With 3.0.6 the driver and the pool answered in one to two milliseconds and stayed there with four callers,
while the mapper call cost 19.5 ms, 39.5 ms with four callers, and still 13.5 ms per call when every call
ran on the one connection of a transaction: not the database, not the driver, not the pool, not the
connection bookkeeping. A Flight Recorder profile of the probe (surefire `argLine`
`-XX:StartFlightRecording=settings=profile`) put 769 of the 924 CPU samples inside the library in one
method, `ProxyInstanceFactory.newInstanceOfInterfaces`, under ByteBuddy's class generation. Its bytecode:
on every call, `new ByteBuddy().subclass(Object).implement(interfaces).method(isPublic())
.intercept(InvocationHandlerAdapter.of(handler)).make().load(loader).getLoaded()`, then a constructor
call; no cache, and none possible since the handler instance is baked into the generated class. It is
called for the parameter handler of every statement, for the type-handler context of every result handler
and for every connection acquired: two generated and loaded classes per query, a third per acquire, each
five to ten milliseconds of CPU on this host, and CPU work scales with concurrency, which is the 80 ms of
section 7e.

The library fixed exactly this in its release 3.1.2 of 2026-07-07 ("memory leak in ProxyInstanceFactory,
Byte Buddy removed", issue 178): the factory now calls `java.lang.reflect.Proxy.newProxyInstance`, and the
JDK keeps one proxy class per class loader and interface set. `mybatis-r2dbc.version` in `db-reactive`
went from `3.0.6.RELEASE` to `3.1.3.RELEASE` (the latest 3.x; built against Boot 3.5.16, MyBatis 3.5.19,
Java 17; the 3.1.x notes list no breaking change or renamed property; ByteBuddy left the compile
classpath). Every service that uses db-reactive gets it on its next build: announce-management, order,
gateway-api and gateway-admin compile against it; db-reactive's suite (70 tests) and the messaging suite
pass. `ReactiveMybatisProxyTest` in db-reactive pins the behaviour (a JDK proxy, one class per interface
set), so a downgrade fails a test instead of showing up as latency.

The service under the inbox load of section 7e (four readers, 60 s), 3.0.6 against 3.1.3: a page of 20
from 165 (222) ms to 47 (138) ms, a page of 50 from 182 (257) ms to 77 (210) ms, 20 to 50 pages per second
on one node, `messaging.query.latency{inbox}` p50 from 180 to 67 ms. The chat load (the projection's cost
per event) is measured in the report of this step.
