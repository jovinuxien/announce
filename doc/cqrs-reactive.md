# cqrs-reactive: the framework every announce service is built on

`cqrs-reactive` is one Maven module (`org.joviste:cqrs-reactive`) with five packages under
`org.linlinjava.announce.cqrs` (and the small `security` package of §5a), plus the sibling test kit `cqrs-reactive-test`. It follows the
[Lagom](https://github.com/lagom/lagom) way of building a service, translated to Spring Boot 3.5 / WebFlux,
OpenCQRS 1.0.0 on EventSourcingDB, R2DBC MySQL and Spring Cloud Stream on Kafka.

| Lagom concept | cqrs-reactive |
|---|---|
| `PersistentEntity` (one entity per id, rebuilt from its events, invariants inside) | one OpenCQRS aggregate per subject `/announce/{id}`; command handlers and state rebuilding handlers per aggregate |
| `PersistentEntityRef.ask(command)` | `ReactiveCommandRouter.send(command, metaData)` returning a `Mono` |
| `ReadSideProcessor` with `globalPrepare`, per-event handlers and an offset store next to the read model | `ReactiveProjection` with `prepare()`, `configure(handlers)`, `reset()`; checkpoint in `cqrs_readside_progress` in the service's MySQL database (`R2dbcProgressTracker`) |
| own your data: other services never read your tables | every service keeps its read tables and checkpoints in its own schema and exposes a public topic and an HTTP API |
| `TopicProducer.taggedStreamWithOffset` | `PublicEventRelay`: an event handling group with its own checkpoint that maps internal events to versioned public events and publishes them at least once |
| `subscribe().atLeastOnce()` | `PublicEventSubscriber`: a Spring Cloud Stream consumer that acks a record only after the handler's `Mono` completes |
| service descriptor + service client with circuit breaker and service locator | the `@HttpExchange` interface in the provider's `<name>-api` module + `HttpExchangeClients` (Eureka/LoadBalancer + Resilience4j) |
| `PersistentEntityTestDriver`, `ReadSideTestDriver`, `ServiceTest.withServer` | `CommandFixture`, `ProjectionHarness`, `CqrsContainers` |

Public contracts (DTOs, public events, `@HttpExchange` interfaces) belong in a per-service `<name>-api`
module, never in the framework.

## 0. Wiring a new service

```xml
<dependency>
    <groupId>org.joviste</groupId>
    <artifactId>cqrs-reactive</artifactId>
</dependency>
<dependency>
    <groupId>org.joviste</groupId>
    <artifactId>cqrs-reactive-test</artifactId>
    <scope>test</scope>
</dependency>
```

Auto-configuration registers everything; the service supplies the connections and names the event
handling groups it owns:

```yaml
spring:
  application:
    name: announce-management
  r2dbc:
    url: r2dbc:mysql://localhost:3306/announce_reactive_db      # read model, checkpoints, command log
    username: announce
    password: ...
  cloud:
    function:
      definition: memberEvents                                  # the subscriber beans (section 3)
    stream:
      bindings:
        announce-out-0:
          destination: announce.v1                              # the public topic of this service
        memberEvents-in-0:
          destination: member.v1                                # a topic this service consumes
          group: announce-management
      kafka:
        binder:
          brokers: localhost:9094

esdb:
  server:
    uri: http://localhost:3000
    api-token: secret

opencqrs:
  event-handling:
    standard:
      fetch:
        subject: /announce            # the aggregate type every group of this service reads
        recursive: true
      retry:
        policy: exponential_backoff   # a failing projection is retried with back-off, then the processor stops
        max-elapsed-time: 1h
    groups:
      announce-summary: {}            # one entry per ReactiveProjection group and per PublicEventRelay group
      announce-public: {}

cqrs:
  readside:
    handler-timeout: 30s              # how long one event handler may take
    progress:
      tracker: r2dbc                  # or in-memory for tests without a database
  command:
    idempotency:
      enabled: true
```

The framework defaults `opencqrs.metadata.propagation` (correlation, causation and command ids flow from a
command into the events it publishes) and `opencqrs.event-handling.standard.progress.tracker-ref` to the
R2DBC tracker. Both can be overridden in the service's own configuration.

The framework creates its two tables in the service database on start-up (`cqrs_command_log`,
`cqrs_readside_progress`); the DDL is in `cqrs-reactive/src/main/resources/cqrs-schema-mysql.sql`.

## 1. `command`: aggregates, subjects, idempotent dispatch, problem details

**Subjects.** An aggregate instance lives under `/<aggregate>/<id>`, children under
`/<aggregate>/<id>/<child>/<childId>`. Use `Subjects.of("announce", id)`, `Subjects.child(...)`,
`Subjects.idOf(subject)`, `Subjects.rootOf(subject)`. Segments are URL-safe; the helpers validate them.

**Commands** implement OpenCQRS `Command` and say whether the subject must be new (`PRISTINE`), must exist
(`EXISTS`) or whatever (`NONE`):

```java
public record PublishAnnounce(String id, String title, long priceCents) implements Command {
    public String getSubject() { return Subjects.of("announce", id); }
    public SubjectCondition getSubjectCondition() { return SubjectCondition.PRISTINE; }
}
```

**Handlers** are OpenCQRS `CommandHandlerDefinition` and `StateRebuildingHandlerDefinition` beans (explicit
style, used by the sample) or `@CommandHandling` / `@StateRebuilding` methods on Spring beans. Business rules
throw `CommandRejectedException(status, code, detail)`; argument problems throw `IllegalArgumentException`.
Event classes must be top-level classes: OpenCQRS uses the class name as the EventSourcingDB event type and
a nested class name contains `$`, which EventSourcingDB rejects.

**Dispatch** goes through `ReactiveCommandRouter`, a `Mono` facade over the blocking OpenCQRS router. The
blocking call runs on the bounded-elastic scheduler, never on a Netty thread.

```java
router.send(new PublishAnnounce(id, title, price), Map.of(MetaDataKeys.COMMAND_ID, commandId), String.class)
```

**Idempotency.** When the metadata carries a `commandId` (for HTTP, take it from an `X-Command-Id` header
the client generates), the router claims the id in `cqrs_command_log` before the handler runs, stores the
JSON result afterwards and releases the claim when the handler fails. A retry with the same id replays the
stored result when a result type is given, and fails with `DuplicateCommandException` (409) otherwise. Two
concurrent dispatches of the same id cannot both run: the primary key on the command id makes one insert
fail.

**Problem details.** `CqrsProblemDetailsHandler` (registered in every WebFlux service) maps command-side
failures to RFC 7807 responses of type `urn:announce:cqrs:<kind>`:

| exception | status |
|---|---|
| `CommandSubjectDoesNotExistException` | 404 |
| `CommandSubjectAlreadyExistsException`, other condition violations, `ConcurrencyException`, `DuplicateCommandException` | 409 |
| `CommandRejectedException` | its status, 409 by default, with a `code` property |
| `IllegalArgumentException` | 400 |
| transient framework errors (EventSourcingDB unavailable) | 503 |
| other framework errors | 500 |

## 2. `readside`: projections and checkpoints

A projection is a bean implementing `ReactiveProjection`:

```java
@Component
class AnnounceSummaryProjection implements ReactiveProjection {
    public String group() { return "announce-summary"; }

    public void configure(ProjectionHandlers handlers) {
        handlers.on(AnnouncePublished.class, (event, envelope) -> upsert(Subjects.idOf(envelope.subject()), event));
        handlers.on(AnnounceWithdrawn.class, (event, envelope) -> delete(Subjects.idOf(envelope.subject())));
    }

    public Mono<Void> prepare() { return createTableIfMissing(); }   // Lagom globalPrepare
    public Mono<Void> reset()   { return truncate(); }               // used by rebuilds
}
```

The framework registers one OpenCQRS event handler definition per projection bean, so the group's
processor delivers every event of the fetch subject; the adapter dispatches by event class and skips
unhandled types. `EventEnvelope` gives the subject, event type, event id, time and metadata (correlation,
causation and command ids). Handlers receive the correlation id in the Reactor context and run inside a
tracing span.

**Threads and blocking.** OpenCQRS processors run on their own threads, call handlers synchronously and
checkpoint after the call returns. The adapter therefore awaits the handler's `Mono` with
`cqrs.readside.handler-timeout`. That blocking is on a processor thread, never on the event loop.

**Checkpoints.** `R2dbcProgressTracker` stores the last handled event id per group and partition in
`cqrs_readside_progress`, in the same database as the read model. Read-model write and checkpoint are two
statements, so a crash in between replays the last event once more: **projections are at-least-once and must
be idempotent** (upsert keyed by the subject, or compare `envelope.eventId()` with the row's last event id).
Ordering per subject is guaranteed by OpenCQRS sequencing (`per_second_level_subject` by default: the
aggregate under the first path segment, so `/conversation/{id}`). The DDL of the framework's three tables is in
`cqrs-reactive/src/main/resources/cqrs-schema-mysql.sql`; they are created on start-up when missing.

**Retries.** A handler error or timeout does not advance the checkpoint; the processor retries with the
group's `retry` policy. When the policy is exhausted the processor stops and logs "giving up"; it restarts
with the service. Give production groups a long `max-elapsed-time`.

**Foreign events.** Every service shares one EventSourcingDB, so a fetch subject can cover events another
service wrote (`/` in the order module, whose payment context owns two roots). The framework registers a
`TolerantEventTypeResolver` (`EventTypeAutoConfiguration`): a stored type name that is no class of this
service resolves to the empty `ForeignEvent` record, which matches no handler and is skipped, instead of
throwing `EventTypeResolutionException` until the retry policy gives up. Unknown types are logged once at
INFO. `cqrs.command.tolerant-event-types=false` restores OpenCQRS's strict resolver. Narrow fetch subjects
per group anyway (`opencqrs.event-handling.groups.<group>.fetch.subject`), reading `/` is wasted work.

**Partitions.** `opencqrs.event-handling.groups.<group>.life-cycle.partitions=N` runs N processor threads for
the group; events are spread over them by subject (sequence resolution `per_second_level_subject`, the default),
so order per aggregate holds and the checkpoint table gets one row per partition (every partition advances its
row past the events it skips too, so idle partitions all sit at the head). OpenCQRS warns that the partition
count must only be changed administratively, with every node stopped, because a partition without a checkpoint
starts at the first event of the log, which for a relay group republishes everything. The framework makes that
step safe: on start-up `R2dbcProgressTracker.seedPartitions` copies the group's lowest existing checkpoint to
every configured partition that has none (`cqrs.readside.progress.seed-partitions`, on by default; a group
with no checkpoint at all is left alone and starts from the beginning like a new projection). Stop all nodes
of the service before raising or lowering the count, as OpenCQRS requires. Raise partitions for projections
whose handler is the bottleneck (a MySQL transaction per event); one Kafka send per event rarely is.

**Leader election: one active copy per group across nodes.** With the default life-cycle controller
(`application_context`) every node starts every processor: the work is done N times and a relay group puts N
copies of each record on its topic. `cqrs.readside.leader-election.enabled=true` creates the bean
`cqrsLockRegistry`, an `R2dbcLockRegistry` over the table `cqrs_readside_lock` in the service's MySQL
database, and OpenCQRS's `life-cycle.controller=leader_election` (its default once a unique `LockRegistry`
bean exists) wraps each processor in a Spring Integration `LockRegistryLeaderInitiator`: the node that holds
the lease of `[group=<g>, partition=<p>]` runs that processor, the others poll for the lease
(`poll-interval`, 250 ms) and stay idle. The holder renews the lease on every heartbeat (500 ms, the
initiator's default); a lease not renewed within `ttl` (10 s) is taken over by another node, so a crashed
node loses its groups after at most the ttl and the successor resumes from the shared checkpoint (at-least-once:
the events in flight at the crash are handled again). Leases are released on a clean shutdown. The registry
is reentrant per node and blocks only on the initiator's own thread. Cost: two short statements per
processor and node every heartbeat, about 4 statements/s per processor per node (12/s measured for one node
with three processors). Note that the first node up takes every lease; a second node is a warm standby, not a
load share, until a lease moves. The measurement with the messaging service is in
`doc/messaging-architecture.md` section 7c.

**Replay and rebuild.** `ProjectionRebuilder.rebuild("announce-summary")` stops the group's processors,
calls `reset()`, drops the group's checkpoints and starts the processors again; they replay the log from the
beginning. Run it on one instance. A new projection needs no rebuild: a group without a checkpoint starts
at the first event. To rebuild into a fresh table instead of truncating the live one, let `reset()` swap
tables.

## 3. `messaging`: public events over Kafka

**Publishing.** Public events are records annotated with `@PublicEvent(type, version)` in the service's
`-api` module. A `PublicEventRelay` is a projection group that maps internal events to them and sends them
through `StreamBridge`:

```java
@Bean
PublicEventRelay announcePublicEvents(PublicEventRelays relays) {
    return relays.forGroup("announce-public").binding("announce-out-0")
            .map(AnnouncePublished.class, AnnouncePublishedV1.class,
                 (event, envelope) -> new AnnouncePublishedV1(Subjects.idOf(envelope.subject()), event.title(), event.priceCents()))
            .map(AnnounceWithdrawn.class, AnnounceWithdrawnV1.class, (event, envelope) -> new AnnounceWithdrawnV1(Subjects.idOf(envelope.subject())))
            .build();
}
```

Each record carries the JSON payload, the subject as Kafka key (one aggregate's events stay in order on one
partition) and the headers `event-type`, `event-version`, `event-id` (the EventSourcingDB id), `subject`,
`correlation-id`, `causation-id`, `producer` and the trace context. Delivery is **at least once**: the
message is sent, then the group's checkpoint advances; a crash in between re-sends it.

**Consuming.** A `PublicEventSubscriber` is a Spring Cloud Stream consumer function:

```java
@Bean
Consumer<Message<byte[]>> announceEvents(PublicEventSubscribers subscribers, AnnounceIndexer indexer) {
    return subscribers.forTopic("announce")
            .on(AnnouncePublishedV1.class, (event, headers) -> indexer.index(event))
            .on(AnnounceWithdrawnV1.class, (event, headers) -> indexer.remove(event.announceId()))
            .build();
}
```

with `spring.cloud.function.definition=announceEvents`, a destination and a consumer group on
`announceEvents-in-0`. The binder calls the consumer on its own thread and commits the offset when it
returns; the handler's `Mono` is awaited with `cqrs.messaging.handler-timeout`. A failure propagates, the
binder retries and can dead-letter the record (`spring.cloud.stream.kafka.bindings.<binding>.consumer.enableDlq`).
The same record can arrive twice: **consumers de-duplicate on `headers.eventId()`**. Unknown event types are
skipped, so a producer can add types without breaking consumers; a new version is a new `@PublicEvent`
class with a bumped `version` and its own handler.

This is how the search context feeds the OCS indexer in `docker/cqrs/docker-compose.yml`: it subscribes to
`announce.v1` and pushes one document per published announce to `http://localhost:8535` through an
`HttpExchangeClients` client pinned with `cqrs.api.clients.ocs-indexer.base-url`.

## 4. `api`: service clients

The provider publishes its descriptor in its `-api` module:

```java
@HttpExchange("/api/announces")
public interface AnnounceApi {
    @GetExchange("/{id}")
    Mono<AnnounceDto> get(@PathVariable String id);
}
```

The consumer turns it into a client:

```java
@Bean
AnnounceApi announceApi(HttpExchangeClients clients) {
    return clients.create(AnnounceApi.class, "announce-management");
}
```

The client is discovered and load-balanced (`http://announce-management` through Spring Cloud LoadBalancer
and Eureka), wrapped in a Resilience4j circuit breaker and time limiter named after the service id
(`resilience4j.circuitbreaker.instances.announce-management.*`), and traced through the auto-configured
`WebClient.Builder`. Connection failures, timeouts and 5xx answers count as breaker failures, 4xx answers do
not. `cqrs.api.clients.<serviceId>.base-url` pins a fixed URL for services outside the registry (the OCS
tier) and for tests. `cqrs.api.clients.<serviceId>.service-token: true` makes the client sign its calls
(§5a); set it for clients of our own services only.

## 5. `observability`: correlation, causation, tracing

- Metadata keys (`MetaDataKeys`): `correlationId`, `causationId`, `commandId`.
- `CorrelationWebFilter` reads or creates `X-Correlation-Id`, puts it in the Reactor context and echoes it
  in the response.
- `ReactiveCommandRouter` fills the correlation id from the Reactor context, else the current trace id, else
  a new UUID, and the causation id from the command id. OpenCQRS propagates both into the events.
- Projection and subscriber handlers run in spans `cqrs.projection`, `cqrs.publish` and `cqrs.subscribe`
  tagged with group, subject, event type and correlation id; Kafka messages carry the trace context
  (`traceparent`) and the subscriber continues the trace.
- Everything degrades to no-ops without a Micrometer `Tracer`; add a tracing bridge
  (`micrometer-tracing-bridge-otel` or `-brave`) to a service to get real spans.

## 5a. `security`: only requests that passed an edge

The edges authenticate people and forward the identity as headers (`X-User-Id`, `X-User-Type`, ...). A service
may trust those headers only if the request really came through an edge, which the edge proves with
`X-Service-Token = v1.<service>.<epochSeconds>.<hex hmacSha256(secret, service:epochSeconds)>` (`ServiceTokens`).

```yaml
announce:
  service-token:
    mode: enforce                       # off (default) | log | enforce
    secret: ${ANNOUNCE_SERVICE_SECRET:dev-service-secret-change-me}
    # max-skew: 60s                     # how old (or how far ahead) a token may be
    # open-paths: /actuator/**          # served without a token
```

- `ServiceTokenWebFilter` (auto-configured, right after the correlation filter) answers 401
  `application/problem+json` to a request, or WebSocket handshake, whose token is missing, stale or forged.
  `log` only warns. The verified caller's name is the exchange attribute `ServiceTokenWebFilter.CALLER_ATTRIBUTE`.
  A checking mode without a secret fails at start-up. Quote `"off"` in YAML or leave the property out: an
  unquoted `off` is the boolean false (accepted, it means off).
- A service that calls another one opts the client in (`cqrs.api.clients.<serviceId>.service-token: true`);
  `ServiceTokenMinter` then signs each call under `spring.application.name`.
- A new service: set the block above in `config/application.yml`, `mode: "off"` in the `test` profile, and let
  the end-to-end suite run with `enforce` through `EdgeRequests` (§6). Read the caller from the headers in one
  place (announce-management: `interfaces.rest.Callers`), checking `X-User-Type` as well as the id.
- Design and rollout: `doc/frontend-edges.md` §10.

## 6. `cqrs-reactive-test`: the test kit

```java
CommandFixture.forAggregate(Announce.class)
        .handlers(AnnounceHandlers.definitions())          // or @CommandHandling / @StateRebuilding objects
        .given(new AnnouncePublished("Bike", 12000))
        .when(new ChangeAnnouncePrice("1", 9900))
        .thenExpectEvents(new AnnouncePriceChanged(9900))
        .thenExpectResult(9900L);
```

`CommandFixture` rebuilds the state from the given events, checks the subject condition, runs the handler
with a capturing publisher and exposes events, result, error and resulting state. No EventSourcingDB.

`ProjectionHarness.of(projection).prepare().feed("/announce/1", event)` runs events through the real
adapter without a processor.

`EdgeRequests` lets a suite run with the service-token check on: put `EdgeRequests.ENFORCE` and
`EdgeRequests.SECRET_PROPERTY` in `@SpringBootTest(properties = ...)` and register
`EdgeRequests.signedWebTestClient()` as a `WebTestClientBuilderCustomizer` bean, and the autowired
`WebTestClient` signs every request like an edge; `EdgeRequests.token()` signs a WebSocket handshake built by
hand; a client from `WebTestClient.bindToServer()` is the caller that bypassed the edges.

Container-backed tests use `CqrsContainers`:

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class AnnounceServiceTest {
    @Container static final EventSourcingDbContainer esdb = CqrsContainers.eventSourcingDb();
    @Container static final KafkaContainer kafka = CqrsContainers.kafka();
    @Container static final MySQLContainer<?> mysql = CqrsContainers.mysql();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) { CqrsContainers.register(registry, esdb, kafka, mysql); }
}
```

The module's own tests contain a complete sample service (`org.linlinjava.announce.cqrs.sample`): a
`Listing` aggregate, a MySQL read model with checkpoints, a public topic and a subscriber of it, driven end
to end over HTTP in `SampleEndToEndTest`. Copy it when starting a service.

Run everything with Docker only:

```
mvn -pl cqrs-reactive,cqrs-reactive-test test
```

## 7. Local stack: `docker/cqrs/docker-compose.yml`

```
docker compose -f docker/cqrs/docker-compose.yml up -d
docker compose -f docker/cqrs/docker-compose.yml --profile tools up -d   # adds Kibana
```

| service | port | used by |
|---|---|---|
| EventSourcingDB 1.2.0 (token `secret`, UI on) | 3000 | every service's command side |
| Kafka 3.9.1 (KRaft, advertised as `localhost:9094`; host port 9094 because litemall's broker holds 9092, topics auto-created) | 9094 | relays and subscribers |
| Elasticsearch 7.17 (with the `analysis-stempel` plugin) | 9200 | the OCS tier |
| OCS indexer | 8535 | announce-management pushes announce and crawled-listing documents here (`SearchIndexer`) |
| OCS searcher | 8534 | the gateway's search API |
| OCS suggest | 8081 | type-ahead |
| Kibana (`tools` profile) | 5601 | inspection |

MySQL is the native server on `localhost:3306` (`announce_reactive_db`) and stays outside the file.

Topics in use: `announce.v1` (announce-management), `auction.v1`, `order.v1`, `payment.v1`,
`shipping.v1` (order module), `messaging.v1` (messaging; `OfferAcceptedV1`), `messaging.live`
(messaging, internal WebSocket fan-out keyed by conversation). The
announce index field configuration is `docker/cqrs/application.indexer-service.yml`, the searcher's
relevance and facets `docker/cqrs/application.search-service.yml`, query-time synonyms
`docker/cqrs/querqy-rules.txt`.

### Topics

One topic per service (or per bounded context of the order module), named `<context>.v1`; a breaking change of a
record is a new version class and, when the whole topic changes shape, a new topic. Records are the `*V1` classes
of the producer's `-api` module.

| topic | producer (module) | records | consumers |
|---|---|---|---|
| `announce.v1` | announce-management (`announce-management-api`) | AnnouncePublishedV1, AnnounceEditedV1, AnnounceWithdrawnV1, AnnounceExpiredV1, AnnounceSoldV1 | order (snapshot, auctions), search, messaging |
| `auction.v1` | order / auction (`order-api`) | AuctionOpenedV1, BidPlacedV1, AuctionClosedWonV1, AuctionClosedUnsoldV1, AuctionSnaggedV1 | order |
| `order.v1` | order / order (`order-api`) | OrderAgreedV1, OrderPaidV1, OrderShippedV1, OrderDeliveredV1, OrderSettledV1, OrderCancelledV1, OrderRefundedV1, PayoutReleaseRequestedV1, RefundRequestedV1 | announce-management (MarkSold, RelistAnnounce), payment, shipping, messaging |
| `payment.v1` | order / payment (`order-api`) | PaymentIntentCreatedV1, PaymentConfirmedV1, PaymentFailedV1, PayoutSentV1, RefundSentV1, SellerAccountUpdatedV1 | order, auction |
| `shipping.v1` | order / shipping (`order-api`) | ShipmentRequestedV1, ShipmentDispatchedV1, ShipmentInTransitV1, ShipmentDeliveredV1, ShipmentFailedV1 | order |
| `messaging.v1` | messaging (`messaging-api`) | ConversationOpenedV1, OfferAcceptedV1 (`messaging.offer-accepted`) | order |

Testing a consumer without its producer: `PublicEventTestProducer` (test kit) sends a record with the relay's
headers to a topic, e.g. `producer.send("order.v1", "/order/42", new OrderPaidV1(...))`.
