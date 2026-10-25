# CLAUDE.md — announce-reactive

## Project

Classifieds / marketplace app (blocket.se, dba.dk, tradera.com style) being
carved out of a litemall fork. Java 21, Spring Boot 3.5.11, Spring Cloud
2025.0.3, WebFlux, reactive MyBatis over R2DBC (MySQL), OpenCQRS 1.0.0 on
EventSourcingDB, DDD bounded contexts (one Maven module per context).

Build facts:
- Parent is `org.joviste:announce:0.0.1-SNAPSHOT`. Run `mvn -N install` once
  so child modules resolve the parent, then build modules with `mvn -pl <module>`.
- Compile with `mvn -P '!webapp' -DskipTests compile`; the announce-management
  `webapp` profile runs `npm run build` and fails locally.
- Modules that compile: config, eureka, gateway-api, gateway-admin, db-reactive,
  cqrs-reactive, announce-management. core, auth-server and order still carry legacy Boot 2 code.
- Build a module together with db-reactive (`mvn -pl db-reactive,<module> ...`) when other
  worktrees may have installed a different db-reactive SNAPSHOT into the shared `~/.m2`.
- Tests in db-reactive and announce-management need the live MySQL database
  `announce_reactive_db` (user `announce`) on localhost:3306. Never touch the
  `litemall` database on the same server.
- `java` on PATH is 11; Maven runs on `/usr/lib/jvm/java-21-openjdk-amd64`.
- announce-management, order and messaging answer 401 to requests without a
  valid `X-Service-Token` (`announce.service-token.mode=enforce`): call them
  through an edge, or start them with `--announce.service-token.mode=off`.
  Their `test` profiles set it off. See `doc/frontend-edges.md` §10.
- Every backend reads its caller from `X-User-Id` + `X-User-Type` (`MEMBER`
  or `ADMIN`) through `cqrs-reactive`'s `Callers`; `X-Seller-Id`,
  `X-Staff-Id` and `X-Admin-Id` no longer exist (`doc/frontend-edges.md` §11).
- Design notes: `doc/announce-management-context.md`, `doc/auction-chat-schema.md`.
- Local stack for manual testing: `scripts/start-stack.sh` (builds what is missing,
  starts docker/cqrs if needed, then announce-management 8085, order 8083, messaging
  8086, customer edge 8090, admin edge 8091) and `scripts/stop-stack.sh`. Addresses,
  test accounts and a walk-through: `doc/stack-test-guide.pdf`
  (rendered by `scripts/stack-test-guide.py`).

Process rule: in a worktree session, write a plan and get approval before
editing code. Commit only when asked. Never commit `node/`, `node_modules/`
or `target/`.

## Per-worktree tasks

> Worktrees are launched with `open-fix-worktrees.sh`. Each Claude session
> opens in `../announce-wt/<short>` on branch `feat/<short>` and is told to
> read the matching `### Worktree: <short>` block below as its sole task.
> Edit the Task / Acceptance lines to redirect a worktree.

### Worktree: `cqrs-reactive` — Lagom-style reactive CQRS framework module (2026-09-21)

- **Task:** grow `cqrs-reactive` into the framework every business service is
  built on. Keep it ONE Maven module with packages `command`, `readside`,
  `messaging`, `api` and `observability`, plus a sibling `cqrs-reactive-test`
  module for the test kit. Do not split into more modules yet.
  1. `command`: keep `ReactiveCommandRouter`; add subject conventions
     (`/announce/{id}`), command-ID idempotency, and error mapping to WebFlux
     problem details.
  2. `readside`: reactive projection adapter over OpenCQRS event handling
     processors (`com.opencqrs.framework.eventhandler`), so a projection can
     return `Mono`/`Flux`; an R2DBC-backed `ProgressTracker` storing the
     checkpoint in MySQL next to the read model; replay/rebuild support.
  3. `messaging`: an event handler group that maps internal events to
     versioned public events and publishes through Spring Cloud Stream
     `StreamBridge` (Kafka); a subscriber helper for consuming another
     service's topic; at-least-once semantics documented.
  4. `api`: factory turning an `@HttpExchange` interface into a discovered,
     load-balanced, Resilience4j-wrapped `WebClient`.
  5. `observability`: correlation and causation IDs in event metadata and
     Kafka headers, wired to Micrometer tracing.
  6. `cqrs-reactive-test`: given/when/then command fixture, projection
     harness, Testcontainers for EventSourcingDB and Kafka.
  7. `docker/` compose file with EventSourcingDB and Kafka for local dev.
  Public contracts (DTOs, public events, `@HttpExchange` interfaces) belong in
  a per-service `<name>-api` module, NOT in the framework.
- **Acceptance:** `mvn -pl cqrs-reactive,cqrs-reactive-test test` passes with
  Testcontainers only (no live MySQL, no running EventSourcingDB); one sample
  aggregate + projection + topic in `cqrs-reactive-test` exercises the whole
  path end to end; announce-management still compiles against the module;
  `doc/cqrs-reactive.md` explains how a new service uses each package.

### Worktree: `announce-kinds` — Announce as goods, job or event (2026-09-22)

- **Prerequisite:** `feat/cqrs-reactive` merged into `announce-jo-branch`.
  Follow `doc/cqrs-reactive.md`; the sample under
  `cqrs-reactive-test/src/test/java/.../sample` is the reference for wiring
  an aggregate, a projection and a topic.
- **Task:** turn the Announce aggregate in `announce-management` into a
  generic announce with three kinds, and rework the `db-reactive` schema so
  the tables become read models fed by projections. Both modules in this one
  worktree.
  1. Domain (`org.linlinjava.announce.goods.domain.announce`): one `Announce`
     aggregate keeps what is shared by every kind: owner (`SellerId`, the
     member who posted it), category, title, description, gallery, keywords,
     `Location`, status lifecycle DRAFT -> PUBLISHED -> SOLD | WITHDRAWN |
     EXPIRED, publishedAt / expiresAt. Add a sealed `AnnounceKind` detail:
     - `GoodsDetails`: price (`Money`), condition, `SaleType` (FIXED_PRICE,
       AUCTION, GIVEAWAY), variants and attributes as today. The only kind
       that is biddable.
     - `JobDetails`: employer, contract type, salary range (optional),
       application deadline, how to apply. Never biddable.
     - `EventDetails`: start and end date-time, venue (may reuse Location),
       organizer, free or ticketed, capacity (optional).
     Kind invariants live in the aggregate: an event cannot publish unless
     start < end and end is in the future; a job or event cannot have an
     auction sale type; a goods announce needs a price unless it is a
     giveaway or auction. SOLD applies to goods only; jobs and events end
     as EXPIRED or WITHDRAWN.
  2. Command side on OpenCQRS: subject `/announce/{id}`; commands
     PostAnnounce (with kind), EditAnnounce, PublishAnnounce,
     WithdrawAnnounce, MarkSold, ExpireAnnounce; events as top-level
     classes (EventSourcingDB rejects `$` in type names), e.g.
     AnnouncePosted, AnnounceEdited, AnnouncePublished, AnnounceWithdrawn,
     AnnounceSold, AnnounceExpired. Dispatch through `ReactiveCommandRouter`.
  3. Schema in `db-reactive/sql`: replace the stretched `announce_goods` by a
     base table `announce` (id, kind, seller_id, category_id, title, brief,
     detail, gallery, keywords, region/city/postal code/coords, status,
     published_at, expires_at, add_time, update_time, deleted) plus one
     detail table per kind keyed by announce_id: `announce_goods_detail`
     (price, condition, sale_type, brand_id, unit), `announce_job`
     (employer, contract_type, salary_min, salary_max, currency,
     application_deadline, apply_url_or_email), `announce_event`
     (starts_at, ends_at, venue, organizer, ticketed, ticket_price,
     capacity). Keep `announce_goods_product`, `announce_goods_attribute`
     and `announce_goods_specification` but key them by announce_id. Add
     `kind` to `announce_category` so goods, jobs and events have separate
     roots. Move the auction columns added to `announce_goods` (seller_id,
     sale_type, expire_time) into the new tables and update
     `announce_auction_chat.sql` and `build_announce_reactive_db.sh`.
     Regenerate the affected reactive mappers with
     `mybatis-generator/generatorConfig.xml` + `reactify_mapper.py`.
  4. Read side: projections in `announce-management` (via the
     `readside` package) write the tables above from the events; the
     `R2dbcProgressTracker` keeps the checkpoint in MySQL. Queries
     (`AnnounceBrowsing`) read the tables, never the event store.
  5. Search: a second projection publishes announces of every kind to the
     OCS indexer configured in `docker/cqrs` (index `announce_index`), with
     `kind` as a facet. Removal on withdraw/expire.
  6. REST (`interfaces/rest`): `/api/announces` accepts a `kind`
     discriminator in the body and filters by kind on listing; the owner
     is the member in `X-User-Id` with `X-User-Type=MEMBER` (`X-Seller-Id`
     was dropped on 2026-10-01, `doc/frontend-edges.md` §10).
- **Out of scope:** bidding (auction context), job applications, ticketing,
  moderation. They reference the announce id later.
- **Acceptance:** `mvn -pl db-reactive test` and
  `mvn -pl announce-management -P '!webapp' test` pass; a test posts one
  announce of each kind, publishes it, and reads it back from the
  projection with the kind-specific fields; an event with end before start
  and a job with AUCTION sale type are rejected; the seed data in
  `announce_data_en.sql` loads into the new schema (existing goods rows
  migrated to kind GOODS); `doc/announce-management-context.md` gains a
  section on the three kinds and the read-model tables.

### Worktree: `crawled-listings` — Crawled listings as a second search source (2026-09-25)

- **Prerequisite:** `feat/announce-kinds` merged into `announce-jo-branch`
  (done). `docker/cqrs` up (Elasticsearch, OCS indexer 8535, searcher 8534,
  suggest). Read `doc/announce-management-context.md` §8 first.
- **Decision (option A):** listings crawled from dba.dk and tradera.com are
  NOT announces. They never enter the event store or the `announce*` read
  models. They live in their own table, are indexed into the same
  `announce_index` with a `source` facet, and a click on a hit redirects to
  the source page. The aggregate, its commands and `AnnounceSearchProjection`
  stay untouched.
- **Task:** add a `listing` sub-context to `announce-management` (package
  `org.linlinjava.announce.listing` with `domain`, `application`,
  `infrastructure`, `interfaces`; nothing in `goods` imports from `listing`
  and vice versa; split into its own module later if it grows) plus schema
  in `db-reactive`.
  1. Schema (`db-reactive/sql/announce_table.sql`, also
     `build_announce_reactive_db.sh`): table `crawled_listing` with
     `id` varchar(64) PK = `{source}-{external_id}` (deterministic: the
     same listing always maps to the same row and the same index document),
     `source` (DBA, TRADERA), `external_id`, `url` (canonical, 1023),
     `content_hash` char(64), `kind` (GOODS now; JOB/EVENT later),
     `title`, `brief` varchar(255) (snippet only, never the full text),
     `image_url`, `price` decimal(10,2), `currency` char(3), `sale_type`
     (FIXED_PRICE, AUCTION), `ends_at` (tradera auctions),
     `source_category` varchar(255) (site's own path, e.g. "Cars/Private
     Car"), `category_id` nullable (mapped `announce_category` of kind
     GOODS), `attributes` JSON (km, model year, ...), `address_raw`,
     `region_id`, `city`, `postal_code`, `published_at` (from the source),
     `first_seen_at`, `last_seen_at`, `status` (ACTIVE, GONE),
     `click_count`, `add_time`, `update_time`; unique key
     (`source`, `external_id`), keys on (`status`, `last_seen_at`) and
     (`source`, `content_hash`). Table `listing_category_map` (`source`,
     `source_category`, `category_id`) so category mapping is data, not
     code. Regenerate mappers with `mybatis-generator/generatorConfig.xml` +
     `reactify_mapper.py`.
  2. Domain: `CrawledListing` entity + `ListingSource` enum + value object
     `CrawledItem` (the normalised shape a parser produces). Rules:
     `brief` is truncated to 255 chars; content hash covers title, brief,
     price, sale_type, ends_at, attributes, image, address; a listing is
     GONE when not seen for `announce.listing.gone-after` (default 7 days)
     and comes back to ACTIVE if seen again.
  3. Feed parsers (`infrastructure.feed`): one per source reading the
     scrapy JSON. `DbaFeedParser` maps `itemName/itemDescription/itemUrl/
     itemImage/itemPris/itemCategory/itemSubCategory/itemKm/itemModelYear/
     itemAddress/itemPublishedDate` (each is a one-element list, values
     carry `\r\n` padding); `external_id` is the `id-<digits>` segment of
     the URL. `TraderaFeedParser` maps `itemId/description/itemType/
     startDate/endDate` with `sale_type` AUCTION and `ends_at` = endDate.
     Address normaliser: "6580 Vamdrup" -> postal_code + city; `region_id`
     from `announce_region` by city name, null when unknown (no geocoding).
  4. Importer (`application`): `ListingImportService.importFile(source,
     path)` streams items, upserts by id, and for each row: new or hash
     changed -> index; unchanged -> touch `last_seen_at` only. A
     `ListingSweepJob` (`@Scheduled`, next to `AnnounceExpiryJob`) marks
     stale rows GONE and removes them from the index. `ListingImportJob`
     polls `announce.listing.import.dir` (default
     `/home/bimeni/announce_crawled_data`), one file per source pattern
     (`dba_*.json`, `tradera_*.json`), and moves a processed file to
     `done/` so a run is never imported twice. Admin triggers: `POST
     /api/admin/listings/import` (file path in body) and
     `POST /api/admin/listings/reindex` (full pass over ACTIVE rows in
     batches of 500; used after an index rebuild).
  5. Index: move `OcsIndexerApi` and `OcsAnnounceIndexer` behind a shared
     `org.linlinjava.announce.search` package used by both sub-contexts
     (one `SearchIndexer` port: `index(id, data)`, `remove(id)`;
     `AnnounceIndexer` delegates to it). In
     `docker/cqrs/application.indexer-service.yml` add `source` (string,
     Result + Facet, values `local`, `dba`, `tradera`; never used as a
     default scope), `external_url` (string, Result), `source_category`
     (string, Result), `ends_epoch` already exists (auction end); extend
     the dynamic-field negative-lookahead with the new names.
     `AnnounceDocument.of` emits `source: local`. Listing documents use the
     same `kind`, `title`, `description`, `price`, `sale_type`, `image_url`,
     `city`, `postal_code`, `region_id`, `category_ids`, `created_epoch`
     names so facets and sorts work across both sources; km and model year
     ride as dynamic attribute facets like goods attributes.
  6. Click-out: `GET /out/{id}` (`interfaces.rest`) increments
     `click_count` and answers 302 to `url`; 410 when the row is GONE, 404
     when unknown. The frontend uses `external_url` only to show the
     domain; the click goes through `/out/{id}`.
  7. Scrapy side (outside this repo, `~/Desktop/python_projects/
     scrapy_projects/tutorial`): document in `doc/crawled-listings.md`
     what the importer expects and fix the pipeline: `DuplicatesPipeline`
     reads JSON lines but `FEEDS` writes an indented array, and it keys on
     `itmeUrl` (typo). Switch the feed to `jsonlines`, one file per run
     named `dba_<yyyyMMddHHmm>.jsonl`, and drop the pipeline's own dedup
     (the importer dedups). Nutch is a later discovery step that feeds
     start URLs to scrapy; nothing here depends on it.
  8. Kafka is the follow-up transport (scrapy producer -> topic
     `crawled-listings` -> `PublicEventSubscribers.forTopic`), not part of
     this task; keep `ListingImportService` transport-agnostic (takes a
     `Flux<CrawledItem>`).
- **Out of scope:** geocoding, image mirroring, jobs/events from crawled
  sources, nutch integration, Kafka transport, frontend changes.
- **Acceptance:** `mvn -pl db-reactive test` and
  `mvn -pl announce-management -P '!webapp' test` pass with
  `announce.search.mode=noop` (fake indexer records calls). A test imports
  a fixture of three dba items and two tradera items twice: five rows,
  five index calls on the first run, zero on the second; a changed price
  yields one reindex; a row with `last_seen_at` older than the grace period
  is set GONE and removed from the index; `GET /out/{id}` redirects for
  ACTIVE and answers 410 for GONE. `AnnounceDocument` carries
  `source=local`. The real `dba_cars.json` in the import dir loads without
  errors. `doc/crawled-listings.md` explains the table, the feed format,
  the index fields and the legal stance (snippet + link out, no full text).

### Worktree: `order-reactive` — order module: auction, deal, payment, shipping (2026-09-26)

- **Prerequisite:** `feat/announce-kinds` merged (done). Design agreed in
  `doc/order-context.md`; read it fully first, then `doc/cqrs-reactive.md` and
  the sample under `cqrs-reactive-test/src/test/java/.../sample`.
- **Task:** replace the legacy litemall `order` module by a reactive module of
  the same name built like `announce-management`, with four package roots
  `org.linlinjava.announce.{auction,order,payment,shipping}`, each a bounded
  context (own aggregate, events, projections, REST) that shares nothing but
  public events. Delete the legacy sources (cart, coupon, groupon, integral,
  Boot 2) once the new module compiles; port nothing.
  0. Public events out of announce-management: new module
     `announce-management-api` with `AnnouncePublishedV1` (id, kind, sellerId,
     title, mainPicture, priceCents, currency, saleType, categoryId,
     expiresAt), `AnnounceEditedV1`, `AnnounceWithdrawnV1`,
     `AnnounceExpiredV1`, `AnnounceSoldV1`; a `PublicEventRelay` group
     `announce-public` on binding `announce-out-0`, topic `announce.v1`.
     Add a subscriber in announce-management on `order.v1` that sends
     MarkSold on `OrderPaidV1` and republishes on `OrderCancelledV1` /
     `OrderRefundedV1` when the announce was sold.
  1. Module skeleton: `order/pom.xml` copied from announce-management
     (db-reactive, cqrs-reactive, cqrs-reactive-test, Kafka, Eureka,
     config); Boot app class; `order-api` module for the `*V1` records of
     `auction.v1` and `order.v1`.
  2. Schema `db-reactive/sql/announce_order.sql` (run after
     `announce_table.sql`, wired into `build_announce_reactive_db.sh`):
     reshaped `announce_order`, new `announce_order_snapshot`,
     `announce_payment`, `announce_seller_account`, `announce_shipment`;
     `announce_auction`, `announce_bid`, `announce_auction_event` moved here
     from `announce_auction_chat.sql` with `goods_id` -> `announce_id`
     varchar(36); drop `announce_order_goods` and `announce_cart`. Regenerate
     mappers with `mybatis-generator/generatorConfig.xml` + `reactify_mapper.py`.
  3. `auction`: `Auction` aggregate on `/auction/{announceId}` with the
     commands/events of `doc/order-context.md` §4 and the rules of
     `doc/auction-chat-schema.md` §3; subscriber on `announce.v1` opening and
     cancelling auctions; `AuctionClosingJob`; projections to the three
     auction tables; REST `/api/auctions/**`; relay to `auction.v1`.
  4. `order`: `Order` aggregate on `/order/{orderId}` with the state machine
     and release rule of §3/§4; `FeeSchedule` policy (percentage + fixed per
     category, from config); subscribers on `announce.v1` (snapshot),
     `auction.v1`, `messaging.v1` (`OfferAcceptedV1`, contract stubbed in
     `order-api` until the messaging worktree ships it); `OrderPayWindowJob`,
     `OrderShipWindowJob`, `OrderReleaseJob` with the windows of §3
     (`order.window.*`, `order.release.on=DELIVERY` default, `DISPATCH`
     allowed); projection to `announce_order`; REST `/api/orders/**` and
     admin dispute resolution; relay to `order.v1`.
  5. `payment`: `Payment` aggregate on `/payment/{orderId}`; `PaymentProvider`
     port; `FakePaymentProvider` with `POST /api/payments/webhook/fake`
     to simulate confirmed / failed / payout-sent / refund-sent; webhook
     signature verification and de-duplication on provider event id;
     seller account onboarding use case and `announce_seller_account`
     projection; `StripeConnectPaymentProvider` as a stub class behind the
     port (real calls are a follow-up).
  6. `shipping`: `Shipment` aggregate on `/shipment/{orderId}`; `Carrier`
     port; `FakeCarrier` with `POST /api/shipping/webhook/fake` to simulate
     dispatched / in-transit / delivered / failed; `ShipmentTrackingJob`
     polling open shipments; projection to `announce_shipment`; REST
     `/api/orders/{id}/shipment`; `PostNordCarrier` stub behind the port.
  7. Cross-context wiring is only through the topics; a test proves no
     root imports another root (ArchUnit or a package-cycle check in the
     build).
  8. `docker/cqrs/docker-compose.yml`: nothing new; document the topics
     `announce.v1`, `auction.v1`, `order.v1`, `messaging.v1` in
     `doc/cqrs-reactive.md` §7.
- **Out of scope:** chat (worktree `messaging`), real Stripe and PostNord
  calls, invoices/VAT, partial refunds, multi-item orders, promoted announces.
- **Acceptance:** `mvn -pl order -P '!webapp' test` and
  `mvn -pl announce-management -P '!webapp' test` pass (Testcontainers for
  EventSourcingDB and Kafka via cqrs-reactive-test, live MySQL for the read
  models as today). End-to-end tests with the fakes: (a) auction: publish an
  AUCTION announce event, two bids, close, order AGREED with the winner,
  fake payment webhook -> PAID, shipment requested, fake carrier dispatched
  then delivered, grace elapsed (clock injected) -> payout released ->
  SETTLED, `OrderPaidV1` and `OrderSettledV1` observed on `order.v1`;
  (b) fixed price: request, accept, pay, ship, deliver, confirm receipt
  before the grace -> SETTLED; (c) offer: `OfferAcceptedV1` in -> order
  AGREED at the offer amount; (d) guards: payout refused while a dispute is
  open, refused before carrier delivery when `release.on=DELIVERY`, allowed
  after dispatch when `release.on=DISPATCH`; ship window expiry refunds and
  emits `OrderRefundedV1`; a bid from the seller and a bid below increment
  are rejected; a duplicate provider webhook is ignored. announce-management
  marks the announce SOLD on `OrderPaidV1`. `doc/order-context.md` status
  line updated to "implemented" with any deviations recorded.

### Worktree: `messaging` — chat between members and with staff (2026-09-26)

- **Prerequisite:** `feat/cqrs-reactive` merged (done). Independent of
  `order-reactive`; the shared contract is `OfferAcceptedV1` on
  `messaging.v1` as written in `doc/order-context.md` §9 (keep the field
  names: conversationId, announceId, buyerId, sellerId, amountCents,
  currency, acceptedAt).
- **Task:** new module `messaging` (+ `messaging-api`), package
  `org.linlinjava.announce.messaging`, built like announce-management.
  1. Aggregate `Conversation` on `/conversation/{id}`: OpenListingConversation
     (announceId, buyerId; one per announce and buyer, idempotent),
     OpenSupportConversation (WAITING until staff joins), Join, PostMessage
     (TEXT, IMAGE, OFFER; idempotent on clientMsgId; sender must be a
     participant; OFFER only in LISTING conversations, only by the buyer),
     AcceptOffer / DeclineOffer (seller only, latest open offer), MarkRead,
     Close, Block (staff). Events ConversationOpened, ParticipantJoined,
     MessagePosted, OfferAccepted, OfferDeclined, ConversationClosed,
     ConversationBlocked.
  2. Schema: `announce_conversation`, `announce_conversation_participant`,
     `announce_chat_message`, `announce_bulletin` move from
     `announce_auction_chat.sql` to `db-reactive/sql/announce_messaging.sql`
     with `goods_id` -> `announce_id` varchar(36), ids varchar(36),
     `last_event_id` on the conversation row; projections write them.
  3. Delivery: WebFlux `WebSocketHandler` at `/ws/conversations/{id}`
     (auth via `X-User-Id` at handshake for now); messages are commands;
     fan-out across nodes through Kafka topic `messaging.live` keyed by
     conversation id so every node with an open socket pushes the event.
     REST fallback `GET /api/conversations`, `GET /api/conversations/{id}/messages?after=`.
  4. Public events: relay `OfferAcceptedV1` (and `ConversationOpenedV1`) to
     `messaging.v1`. Subscribe to `order.v1` and post SYSTEM messages into
     the listing conversation on OrderAgreedV1, OrderPaidV1, OrderShippedV1,
     OrderSettledV1 (contract from `order-api`; stub the records locally if
     that module is not merged yet).
  5. Support queue: `GET /api/admin/conversations?status=WAITING`, staff
     Join moves WAITING -> OPEN; bulletins `POST /api/admin/bulletins`
     fan out to connected staff by client type.
- **Out of scope:** push notifications, attachments storage (URLs only),
  moderation/abuse tooling beyond Block, read receipts per message.
- **Acceptance:** `mvn -pl messaging test` passes on Testcontainers plus live
  MySQL for the read model; a test opens a listing conversation, exchanges
  messages over two WebSocket clients on one node, posts an OFFER, the
  seller accepts, `OfferAcceptedV1` is observed on `messaging.v1` with the
  agreed field names; a second buyer cannot post in that conversation; a
  duplicate clientMsgId posts once; a support conversation is WAITING until
  staff joins; `doc/order-context.md` §9 updated with any deviation.

### Worktree: `gateway-api` — customer edge and SPA (2026-09-26)

- **Prerequisite:** `feat/crawled-listings` merged (done). Design in
  `doc/frontend-edges.md`; read it fully, then litemall's
  `~/shopping_apps/litemall_app/litemall/litemall-gateway-api` (the model:
  `pom.xml` webapp profile, `gatewayapi/{auth,security,machine,web}`,
  `src/main/webapp/app/{Layout.tsx,config,shared,modules/search}`).
- **Task:** new module `gateway-api` (port 8080) = Spring Cloud Gateway edge
  + React SPA `@announce/customer-web`, replacing the `gateway` module.
  1. Module: `pom.xml` from the existing `gateway` module (parent
     `org.joviste:announce`, `spring-cloud-starter-gateway-server-webflux`,
     Eureka, config client, `db-reactive` for the user and refresh-token
     mappers) plus the `webapp` profile with `frontend-maven-plugin`
     (node v20.16.0 / npm 10.8.1, `workingDirectory` `src/main/webapp`,
     output copied to `static/`). `-P '!webapp'` must build without node.
  2. Identity: `auth/AuthController` (`/auth/login|register|refresh|logout|me`)
     over `announce_user` (BCrypt, username or mobile), RS256 JWT
     (`announce.jwt.*`, dev keypair in config, 2 h access, refresh token
     hashed in new table `announce_refresh_token` in `db-reactive/sql`,
     httpOnly cookie on `/auth`); `security/SecurityConfig` deny by default
     with `PublicPaths`; `security/IdentityForwardingFilter` strips client
     `X-User-*`, `X-Staff-Id`, `X-Admin-Id`, `X-Seller-Id` and
     `X-Service-Token`, injects `X-User-Id` and `X-User-Type=MEMBER`
     (announce-management reads the owner from them; do not reintroduce
     `X-Seller-Id`); `machine/MachineTokenRelayFilter` adds
     `X-Service-Token`, which the backends verify.
  3. Routes of `doc/frontend-edges.md` §3 in `config/application.yml`
     (explicit, ordered, no StripPrefix), the OCS `SetPath` routes with
     fixed URLs from config (`announce.search.searcher-url`,
     `announce.search.suggest-url`), the `lb:ws://messaging` route, and
     `web/SpaHistoryFallbackFilter`.
  4. SPA phase 1 (`doc/frontend-edges.md` §5): shell, auth, home, search
     (facets from the OCS response, `source` badge and `/out/{id}` link),
     announce detail per kind, post wizard per kind with the aggregate's
     validation rules, my announces. i18n `en`, `da`, `sv` with
     `npm run i18n:check`. Typed API clients in `app/shared/api` generated
     by hand from the controllers' DTOs (`AnnounceDtos`, category, brand).
  5. SPA phase 2 behind `features.trade` and `features.chat` with MSW
     mocks against `doc/order-context.md` §8/§9: auction panel, buy flow,
     orders, chat. Mocks live in `app/mocks`; the flags default off.
  6. Delete the `gateway` module (`gateway/`, its entry in the root
     `pom.xml`, `docker/`, `deploy/` references) and add `gateway-api` to
     `docker/docker-compose.yml`.
- **Out of scope:** admin edge (worktree `gateway-admin`), image upload,
  SEO head/sitemap, Google sign-in, password reset mail, TLS.
- **Acceptance:** `mvn -pl gateway-api -P webapp -DskipTests package`
  produces a jar serving the SPA at `/`; `mvn -pl gateway-api -P '!webapp'
  test` passes: route tests with MockWebServer (public GET without token
  OK, write without token 401, valid token -> `X-User-Id` and a
  verifiable `X-Service-Token` downstream, spoofed `X-User-Id` and staff
  headers dropped, SPA fallback,
  search SetPath, refresh rotation, logout revokes); `npm test` and
  `npm run i18n:check` pass; with announce-management, db-reactive's MySQL
  and `docker/cqrs` running, the manual checklist in
  `doc/frontend-edges.md` §7 is executed and recorded there (register, post
  one announce of each kind, publish, find in search, click out of a
  crawled listing).

### Worktree: `gateway-admin` — admin edge and SPA (2026-09-26)

- **Prerequisite:** `feat/crawled-listings` merged (done). Independent of
  `gateway-api` (shares the design, duplicates the security package as
  litemall does; extraction into a shared module is a later step). Design
  in `doc/frontend-edges.md`; model: litemall's `litemall-gateway-admin`
  (`ROUTING.md`, `gatewayadmin/web/admin/EdgeAdmin*`, admin SPA routes).
- **Task:** new module `gateway-admin` (port 8081) = gateway edge + React
  SPA `@announce/admin-web`.
  1. Module and build as in the gateway-api block (same pom shape, same
     webapp profile, package `org.linlinjava.announce.gatewayadmin`).
  2. Identity: admin `AuthController` over `announce_admin` with
     `role_ids` -> `announce_role` / `announce_permission` (tables exist in
     `announce_table.sql`); JWT carries roles; `IdentityForwardingFilter`
     injects `X-User-Id`, `X-User-Type=ADMIN`, `X-User-Roles`; refresh
     tokens in `announce_refresh_token` with `user_type=ADMIN`.
  3. Routes of `doc/frontend-edges.md` §4; `ROLE_ADMIN` on `/api/**`,
     `ROLE_SUPER` on admins and roles.
  4. Edge-hosted reactive controllers over `db-reactive` mappers:
     `/api/admin/users`, `/api/admin/admins` (BCrypt on create),
     `/api/admin/roles`, `/api/admin/permissions`, `/api/admin/regions`,
     `/api/admin/dashboard` (counts from `announce`, `crawled_listing`,
     `announce_user`).
  5. In announce-management (small, coordinated change): `AnnounceAdminController`
     at `/api/admin/announces` (list all with kind/status/seller/region/q
     filters, `POST {id}/withdraw`, `POST {id}/expire`, no owner header,
     reason recorded) dispatching the existing commands.
  6. SPA phase 1 (`doc/frontend-edges.md` §6): login, dashboard,
     categories per kind, brands, announces moderation, crawled listings
     (import, reindex, stats), members, admins/roles/permissions, regions.
     Phase 2 behind `features.trade` / `features.chat` with MSW mocks:
     auctions, orders and disputes, payouts, support queue and bulletins.
  7. Add `gateway-admin` to `docker/docker-compose.yml`.
- **Out of scope:** customer edge, OCS configuration UI, audit log,
  statistics beyond counts.
- **Acceptance:** `mvn -pl gateway-admin -P webapp -DskipTests package`
  serves the SPA; `mvn -pl gateway-admin -P '!webapp' test` passes: route
  tests (no token 401, member token 403, admin token -> `X-User-Roles`
  downstream, super-only paths), edge controllers tested against the live
  MySQL like db-reactive; `mvn -pl announce-management -P '!webapp' test`
  still passes with the new admin controller covered; `npm test` and
  `npm run i18n:check` pass; manual checklist in `doc/frontend-edges.md`
  §7 executed and recorded (log in, create a category of each kind,
  withdraw an announce, trigger a listings import, create an admin with a
  role).

### Worktree: `crawler-inventory` — fresh crawled data and a listing inventory (2026-10-17)

- **Prerequisite:** `feat/crawled-listings` merged (done). Read
  `doc/crawler-inventory-plan.md` first: it holds the 2026-09-29 survey of the
  current dba.dk / tradera.com markup, the approved decisions and the step
  details; this block is its summary. Then `doc/crawled-listings.md`. The
  Java importer (`org.linlinjava.announce.listing`), `CrawledItem`,
  `ListingImportService`, `ListingSweepJob` and the admin triggers exist and
  stay; the old spiders in `~/Desktop/python_projects/scrapy_projects/tutorial`
  are dead against today's sites.
- **Decisions (approved):** (1) the scrapy project moves into the repo as
  `crawler/` (package, `scrapy.cfg`, pinned `requirements.txt`; venv, eggs,
  build, dbs, logs, .idea ignored); the Desktop folder becomes a symlink after
  merge. (2) Spiders emit the importer's own shape: one flat JSON line per
  item with `schema: "crawled-item/2"` and the fields `externalId, url,
  title, brief, imageUrl, price, currency, saleType, endsAt, sourceCategory,
  attributes{}, addressRaw, publishedAt, crawledAt`; one generic
  `CrawledItemV2Parser` on the Java side, `DbaFeedParser` /
  `TraderaFeedParser` kept for the legacy fixtures. (3) "available and good
  candidate" = a quality grade computed at import plus liveness (`status`
  ACTIVE/GONE and a bounded HEAD-check job); only graded rows are indexed.
- **Task:**
  1. `crawler/` with the scrapy project, `run.sh` (both spiders into
     `announce.listing.import.dir`, files `dba_<stamp>.jsonl` /
     `tradera_<stamp>.jsonl`), a modern honest user agent, robots obeyed,
     page and item caps as spider arguments (`-a max_pages=`), polite delays
     and autothrottle.
  2. `dba_cars`: iterate `/mobility/search/car?registration_class=1&page=N`
     (48 per page), parse the JSON-LD `CollectionPage.mainEntity` product
     list; `externalId` = digits of `/mobility/item/{id}`; `sourceCategory`
     "Cars/Private Car" (other `registration_class` values as further
     categories); optional `-a detail=1` pass (1.5 s delay, capped) adding
     km, model year, address and the full image list from the detail page.
  3. `tradera_cars`: iterate `/category/1001?paging=N`, parse the
     `div[data-item-card-id]` cards; `saleType` from `data-item-type`;
     currency SEK; attributes year / km (mil x 10) / fuel; `publishedAt`
     from the relative "Inkom … sedan"; optional detail pass for `endsAt`
     with null fallback.
  4. Offline spider tests (pytest, `scrapy.http.HtmlResponse` over saved
     HTML fixtures of today's pages) so a markup change fails a test instead
     of silently emptying the feed.
  5. `listing.domain.ListingQuality`: grade GOOD / WEAK / REJECT plus an
     `issues` list. GOOD = title, https image on a known host, canonical url
     matching the source pattern, price > 0 or AUCTION, published date. WEAK
     = one soft field missing (published date, brief, attributes). REJECT =
     url, title or image missing.
  6. Schema (`db-reactive/sql/announce_table.sql`,
     `build_announce_reactive_db.sh`, mappers regenerated with
     `mybatis-generator/generatorConfig.xml` + `reactify_mapper.py`):
     `quality_grade varchar(10)`, `quality_issues json`, `url_checked_at`,
     `url_ok tinyint`, `image_ok tinyint` on `crawled_listing`; key on
     (`status`, `url_checked_at`).
  7. `ListingLivenessJob` (`@Scheduled`, next to `ListingSweepJob`): HEAD
     the url and image of the N oldest-checked ACTIVE rows per run
     (`announce.listing.liveness.batch`, default 100), one host at a time
     with a delay; 404/410 on the url -> GONE at once and index removal;
     dead image -> grade downgraded and reindexed. `WebClient` behind a
     `LinkChecker` port, fake in tests.
  8. Index gating: `announce.listing.index-grades` (default GOOD, WEAK);
     REJECT rows stay in the table with their issues and are never indexed.
     `ImportReport` gains counts per grade.
  9. `GET /api/admin/listings/inventory`: per source ACTIVE, GONE, per
     grade, missing image, dead link, last import time and file. Read-model
     query only.
  10. Docs: `doc/crawled-listings.md` gains the v2 feed shape, the quality
      rules, the liveness job and the crawler runbook; the plan's status
      line records deviations.
- **Out of scope:** image mirroring, Kafka transport, jobs/events from
  crawled sources, Nutch, frontend changes.
- **Acceptance:** `mvn -pl db-reactive test` and
  `mvn -pl announce-management -P '!webapp' test` pass with
  `announce.search.mode=noop`; `pytest` in `crawler/` passes offline. A real
  run of both spiders writes `dba_<stamp>.jsonl` and `tradera_<stamp>.jsonl`
  that import with zero rejected rows and at least 90 % GOOD. A fixture item
  without image imports as REJECT with no index call; a stubbed 410 on a url
  marks the row GONE and removes it from the index; a stubbed dead image
  downgrades to WEAK and reindexes. The inventory endpoint returns the
  expected counts for a five-row fixture.

### Worktree: `staff-permissions` — staff permissions checked by the edge and the backends (2026-10-17)

- **Prerequisite:** nothing in flight; branch off `announce-jo-branch`. Read
  `doc/frontend-edges.md` §11 (the caller convention, its "Still open"
  paragraph is this task) and `doc/cqrs-reactive.md` §5a first. Facts: the
  admin edge (`gateway-admin`, `auth/AdminAccounts`) derives only two
  authorities, `ROLE_ADMIN` for any enabled role and `ROLE_SUPER` when a
  role holds the permission `*`; `security/SecurityConfig` enforces
  `ROLE_ADMIN` on `/api/**` and `/ws/**` and `ROLE_SUPER` on
  `/api/admin/admins|roles|permissions/**`; `X-User-Roles` carries those two
  values downstream, where `cqrs-reactive`'s `Caller` keeps them in `roles`
  but every staff route only calls `Callers.staff(headers)`. So any staff
  account can moderate announces, resolve disputes, release payouts, block
  conversations and send bulletins. `web/admin/PermissionCatalogue` already
  lists the permission codes (`admin:announce:moderate`,
  `admin:catalog:manage`, `admin:listing:manage`, `admin:member:manage`,
  `admin:region:manage`, `admin:order:manage`, `admin:support:manage`,
  `admin:dashboard:read`, and `*`), the roles screen assigns them, but the
  seed roles 2 (Marketplace administrator) and 3 (Support administrator) in
  `db-reactive/sql/announce_data_en.sql` still hold litemall's
  `admin:category:*` strings and nothing reads any of them.
- **Task:** permissions become real, end to end, keeping one convention.
  1. Edge authorities: `AdminAccounts` adds the enabled, non-deleted
     permission codes of the account's enabled roles to the authorities
     next to `ROLE_ADMIN` / `ROLE_SUPER` (`*` expands to every code of the
     catalogue; unknown codes are dropped and logged once). They travel in
     the JWT, in `/auth/me` (`AdminSummary.roles`, keep the field name so
     the SPA's `selectRoles` keeps working) and in `X-User-Roles`
     (`IdentityForwardingFilter`), comma separated as today. A changed
     role takes effect at the next access token (2 h) or refresh; document
     it, no revocation list.
  2. Edge path rules in `SecurityConfig`, one line per route group, super
     paths unchanged: `/api/admin/announces/**` -> `admin:announce:moderate`;
     `/api/admin/categories/**`, `/api/admin/brands/**` ->
     `admin:catalog:manage`; `/api/admin/listings/**` ->
     `admin:listing:manage`; `/api/admin/users/**` -> `admin:member:manage`;
     `/api/admin/regions/**` -> `admin:region:manage`;
     `/api/admin/auctions|orders|payouts|bids/**` -> `admin:order:manage`;
     `/api/admin/conversations/**`, `/api/admin/bulletins/**`, `/ws/**` ->
     `admin:support:manage`; `/api/admin/dashboard/**` ->
     `admin:dashboard:read`. Anything else under `/api/**` stays
     `ROLE_ADMIN`. 403 as a problem detail with code `missing-permission`
     and the permission in `detail`.
  3. Framework: `Caller.can(String permission)` (true for `*`, `ROLE_SUPER`
     or the code itself) and `Callers.staff(HttpHeaders, String permission)`
     which answers 403 `missing-permission` after the existing checks;
     `doc/cqrs-reactive.md` §5a gains the rule. The permission codes are
     plain strings in the framework, no catalogue there.
  4. Backends, every staff route and handshake passes its permission:
     announce-management `AnnounceAdminController` (`admin:announce:moderate`)
     and `ListingAdminController` (`admin:listing:manage`), its category and
     brand admin endpoints if any (`admin:catalog:manage`); order
     `AuctionController`, `OrderController`, `PaymentController`,
     `ShipmentController` admin routes (`admin:order:manage`); messaging
     `Principals.staff` callers in `AdminMessagingController`,
     `StaffWebSocketHandler` and the staff path of
     `ConversationWebSocketHandler` (`admin:support:manage`). The
     per-context helpers (`goods.interfaces.rest.Callers`,
     `messaging.interfaces.Principals`) gain the overload; controllers do
     not import the framework class directly.
  5. Seed: `gen_seed_en.py` and `announce_data_en.sql` /
     `announce_reseed_en.sql` give role 2 `admin:announce:moderate`,
     `admin:catalog:manage`, `admin:listing:manage`, `admin:member:manage`,
     `admin:region:manage`, `admin:dashboard:read` and role 3
     `admin:support:manage`, `admin:dashboard:read`; role 1 keeps `*`. The
     litemall `admin:category:*` strings go. `reseed_en.sh` applies it to
     the live `announce_reactive_db` only when the user says so.
  6. Admin SPA: nav entries and routes gated by permission
     (`RequirePermission` next to `RequireRole`, `selectCan(code)` in
     `authSlice`, `*` and `ROLE_SUPER` grant all); a screen the account
     may not use is hidden from the nav and answers a "no permission" page
     on a direct URL; the role editor's permission picker reads
     `GET /api/admin/permissions` as today. i18n `en`, `da`, `sv`.
  7. Docs: `doc/frontend-edges.md` gains a section "Staff permissions"
     that closes the "Still open" paragraph of §11 (table route group ->
     permission, token lifetime caveat, how to add a permission: catalogue
     + edge rule + backend route + seed + nav).
- **Out of scope:** per-record ownership for staff, audit log, immediate
  revocation, member-side permissions, new screens.
- **Acceptance:** `mvn -pl gateway-admin -P '!webapp' test` passes with new
  route tests: a token of a support-only account gets 403
  `missing-permission` on `POST /api/admin/announces/{id}/withdraw` and 200
  on `GET /api/admin/conversations`, and the proxied request carries
  `X-User-Roles` with `ROLE_ADMIN` and the permission codes; a super token
  passes every group. `mvn -pl cqrs-reactive test` covers `Caller.can` and
  the overload. `mvn -pl announce-management -P '!webapp' test`,
  `mvn -pl order -P '!webapp' test` and `mvn -pl messaging test` pass with
  one test per module proving that a staff caller without the permission is
  refused with 403 `missing-permission` and one with it (or `*`) is served;
  the staff WebSocket handshake in messaging refuses a caller without
  `admin:support:manage`. `npm test` and `npm run i18n:check` pass in
  `gateway-admin`; a jest test shows the nav without the trade entries for a
  support-only account. The seed loads (`mvn -pl db-reactive test`). Manual:
  log in as admin123 (super), create an admin with role 3 only, log in as
  it and confirm the support queue works while the announces screen is
  hidden and `POST /api/admin/announces/{id}/withdraw` answers 403; recorded
  in `doc/frontend-edges.md` §7.

### Worktree: `service-descriptor` — Lagom-style service descriptor for announce-management (2026-10-22)

- **Prerequisite:** nothing in flight; branch off `announce-jo-branch`. Read
  `doc/cqrs-reactive.md` §0 and §4, then `AnnounceController`,
  `AnnouncePublishing`, `AnnounceMessaging` (announce-management) and
  `messaging/.../infrastructure/announce/{AnnounceApi,AnnounceLookupConfig}`
  plus `messaging/.../application/AnnounceRef`. Facts: the Lagom split is
  already in place except for one piece. `ReactiveCommandRouter.send` is
  `entityRef.ask`; `X-Command-Id` -> `CommandIdStore` is the idempotent
  dispatch; the relay group `announce-public` in `AnnounceMessaging` is the
  `TopicProducer` reading the journal (checkpointed, at-least-once to
  `announce.v1`); `AnnounceTopics` in `announce-management-api` is the
  topic on the descriptor; `HttpExchangeClients` is the service client.
  Missing: a shared `@HttpExchange` descriptor. messaging carries a private
  copy (`AnnounceApi` + `AnnounceRef`, comment "until an
  announce-management-api module publishes the descriptor") against
  `GET /api/announces/{id}`, the only cross-service REST call today.
  Spring Boot 3.5 = Framework 6.2: `@HttpExchange` interfaces are valid
  server-side mappings, so a `@RestController` can implement the descriptor
  without duplicating `@RequestMapping`; a class or method must not mix the
  two annotation families.
- **Decisions (agreed 2026-10-22):** (1) the write path never calls Kafka;
  the relay stays the only producer (no `StreamBridge.send` in a service or
  controller). (2) Interface parameters are client-legal only:
  `@PathVariable`, `@RequestParam`, named `@RequestHeader`, `@RequestBody`.
  No bare `HttpHeaders`, so caller identity leaves the signature and is
  resolved once by a `WebFilter` into the Reactor context. (3) Read slice
  first (`view`, `browse`), writes after the caller filter exists.
  (4) `AnnouncePublishing` stays as the application layer (reference checks;
  also used by the `order.v1` subscriber, `AnnounceExpiryJob`,
  `AnnounceAdminController`); the implementing controller stays thin.
  (5) `announce-management-api` keeps depending on `cqrs-reactive` only:
  DTOs that move there get api-level enums, never domain imports.
  (6) Nothing in `cqrs-reactive` main code changes; the framework gains a
  sample and a doc section.
- **Task:**
  1. Descriptor in `announce-management-api`, package
     `org.linlinjava.announce.goods.api`: `@HttpExchange("/api/announces")
     AnnounceApi` with `@GetExchange("/{id}") Mono<AnnounceResponse> view`
     and `@GetExchange Mono<PageResponse<AnnounceSummaryResponse>> browse`
     (every filter as `@RequestParam(required = false)`, `page`, `size`,
     `sort` with the controller's defaults). Move `AnnounceResponse`,
     `AnnounceSummaryResponse`, `PageResponse`, `LocationDto`, `GoodsDto`,
     `JobDto`, `EventDto` from `AnnounceDtos` into the api module as
     `AnnounceApiDtos`, with api enums `AnnounceKindV1`, `AnnounceStatusV1`,
     `SaleTypeV1` (string-serialised, same names as the domain enums). The
     `of(Announce)` factories stay in announce-management (a mapper class
     next to the controller); the api records hold no logic.
  2. Caller filter in announce-management, `goods.interfaces.rest`:
     `CallerContextFilter` (`WebFilter`, after `ServiceTokenWebFilter`)
     parses `X-User-Id` / `X-User-Type` through the framework `Callers` and
     writes the `Caller` (or none) into the Reactor context under one key.
     `Callers.member()`, `viewer()` and `staff(permission)` gain no-arg
     overloads returning `Mono<SellerId>` / `Mono<Integer>` read from the
     context; the `HttpHeaders` overloads stay for the admin and listing
     controllers until they are migrated (not in this task). Same mechanism
     is NOT added to messaging or order here.
  3. `AnnounceController implements AnnounceApi`: drop the class-level
     `@RequestMapping` and the `@GetMapping` lines on `view` and `browse`
     (the interface carries them); keep `@PostMapping` / `@PutMapping` for
     the writes on the class exactly as today. Mixing is legal across
     methods, not on one method. `view` and `browse` read the viewer from
     the context.
  4. messaging: delete `infrastructure/announce/AnnounceApi` and
     `application/AnnounceRef`; `AnnounceLookupConfig` creates
     `clients.create(org.linlinjava.announce.goods.api.AnnounceApi.class,
     "announce-management")` and `AnnounceLookup` maps `AnnounceResponse`
     to what `Conversations` needs (`sellerId`, `title`, `status`). Add
     `announce-management-api` to `messaging/pom.xml`; keep
     `cqrs.api.clients.announce-management.service-token=true` and the
     `messaging.announce-lookup=stub` test switch.
  5. Framework sample (`cqrs-reactive-test`, `sample` package):
     `ListingApi` (`@HttpExchange("/listings")`, `view` + `post` with
     `@RequestHeader(value = "X-Command-Id", required = false)`)
     implemented by `ListingController`; `SampleEndToEndTest` adds one
     round trip through `clients.create(ListingApi.class, ...)` with
     `cqrs.api.clients.<id>.base-url` pinned to the test server and the
     service token on, proving the same JSON the controller serves.
  6. Docs: `doc/cqrs-reactive.md` §4 gains "Implementing the descriptor"
     (server-side `@HttpExchange`, client-legal parameters, caller filter,
     no mixing on one method, the relay is the only producer) and the Lagom
     table row for "service implementation implements the descriptor";
     `doc/announce-management-context.md` records the api module layout.
     Session report PDF in `doc/` as usual.
- **Out of scope:** moving the member writes (post, edit, publish,
  withdraw, sold) onto the interface, admin/listing controllers on the
  caller filter, descriptors for order and messaging, DTO versioning beyond
  the `V1` enums, any change in `cqrs-reactive` main code.
- **Acceptance:** `mvn -pl announce-management-api,announce-management
  -P '!webapp' test` and `mvn -pl messaging test` pass (Testcontainers +
  live MySQL as today); messaging has no local copy of the announce
  descriptor and compiles against `announce-management-api`; a test in
  announce-management calls `view` and `browse` through
  `HttpExchangeClients` (base-url pinned, service token on) and gets the
  same body the controller serves directly; a request without
  `X-User-Id` still sees only PUBLISHED announces and a request with
  `X-User-Id` sees its own drafts (caller filter); `mvn -pl
  cqrs-reactive,cqrs-reactive-test test` passes with the sample round
  trip; `doc/cqrs-reactive.md` §4 updated. Manual: with the stack up, a
  member opens a listing conversation in the customer SPA and the seller is
  resolved through the shared descriptor (messaging log shows the call).

### Worktree: `listing-availability` — crawled listings stay clickable (2026-10-25)

- **Prerequisite:** nothing in flight; branch off `announce-jo-branch` (the
  `crawler-inventory` worktree holds the history of `crawler/` and the
  listing sub-context). Read `doc/crawled-listings.md` §3, §4b, §5, §7 and
  §11, then `ListingSweepService`, `ListingLivenessService`,
  `ListingImportService`, `ListingClickOutController` and
  `gateway-api/.../shared/components/AnnounceCard.tsx`. Facts (survey
  2026-10-25 on the live `announce_reactive_db`): every one of the 693
  `crawled_listing` rows is GONE. 400 tradera and 238 dba rows came from the
  single crawl of 2026-10-17 and were set GONE by the hourly sweep on
  2026-10-24 (`gone-after` P7D); 54 more are the 2024 legacy `dba_cars.json`
  (old `/.../id-N/` urls, 404) that the import flow re-imports because its
  regex `^(dba|tradera)_.*\.jsonl?$` also matches `dba_cars.json`. The OCS
  index still served all of them: the home and search cards link to
  `/out/{id}`, which answers the 410 problem JSON in a new tab, and that is
  the "announce is gone from its source" screen. Two defects behind it:
  (1) the table and the index disagree, because `indexer.remove` in the
  sweep has no retry and nothing reconciles (`ListingLivenessService`
  swallows a failed remove per row too); (2) the sweep calls a listing GONE
  on age alone, while a spot check of a swept dba row (item 12184677)
  answers 200 with the live ad. HEAD liveness cannot see "sold": dba.dk and
  tradera.com answer 200 for a sold or ended ad; only a deleted ad is 404.
  Nothing schedules `crawler/run.sh`; the crawl has not run since 10-17.
- **Decisions:** (a) the list page a spider just parsed is the proof of
  availability, so a fresh feed is indexed as today and a stale feed is
  not; (b) "not seen" is not "gone": a third status STALE sits between
  ACTIVE and GONE; only a verification (status spider, 404/410) sets GONE;
  (c) the table records what the index holds and a job repairs the
  difference; (d) a crawled hit opens an SPA page that can show "no longer
  available" instead of the raw 410; (e) verification is a scrapy spider,
  not a Java GET-and-parse, so one place knows the sites' markup.
- **Task:**
  1. Schema (`db-reactive/sql/announce_table.sql`,
     `build_announce_reactive_db.sh`, mappers regenerated with
     `mybatis-generator/generatorConfig.xml` + `reactify_mapper.py`):
     `crawled_listing.status` gains STALE; new columns `index_state`
     varchar(16) (NONE, INDEXED, PENDING_INDEX, PENDING_REMOVE),
     `indexed_at`, `source_status` varchar(16) (what the last status check
     saw: AVAILABLE, SOLD, ENDED, REMOVED), `source_checked_at`; key on
     (`index_state`), key on (`status`, `source_checked_at`).
  2. Index bookkeeping (`listing.application`): every `index`/`remove`
     goes through one `ListingIndexWriter` that sets `PENDING_*` before the
     call and `INDEXED`/`NONE` after success; `ListingIndexReconcileJob`
     (`@Scheduled`, `announce.listing.reconcile.cron`, default every 5 min)
     retries `PENDING_*` rows in batches and, once a day, removes the
     document of every non-ACTIVE row still `INDEXED`. `POST
     /api/admin/listings/reindex` first deletes the documents of rows that
     may not be shown, then indexes the rest. Inventory gains the four
     `index_state` counts and `GET /api/admin/listings/inventory` the
     difference between indexable rows and `INDEXED` rows.
  3. Lifecycle: `gone-after` becomes `announce.listing.stale-after`
     (default P2D): the sweep sets STALE and removes the document; the
     importer turns a STALE or GONE row ACTIVE again when a feed shows it
     (as today for GONE). `announce.listing.max-feed-age` (default P1D): an
     item whose `crawledAt` is older is upserted as STALE and not indexed.
     `ListingLivenessService` keeps the image HEAD and the 404/410 -> GONE
     rule but no longer treats 200 as proof of anything. `/out/{id}`: 302
     for ACTIVE and STALE (the ad may well be live), 410 for GONE, 404
     unknown. `POST /api/admin/listings/purge` with `{status: GONE,
     olderThan: P30D}` deletes rows (and their documents) for hygiene.
     Import flow regex tightened to `^(dba|tradera)_\d{12}\.jsonl$`; the
     legacy `dba_cars.json` moves to `crawler/tests/fixtures/legacy/`.
  4. Status feed from scrapy: `ListingStatusExportJob` (`@Scheduled`,
     `announce.listing.status.cron`, default every 6 h) writes
     `<import.dir>/recheck/<source>.urls` (one url per line, oldest
     `source_checked_at` first, STALE rows first, batch
     `announce.listing.status.batch` default 200). New spiders
     `dba_status` and `tradera_status` in `crawler/` read that file, fetch
     each item page politely (1.5 s delay, caps as `-a` arguments) and emit
     one line per url with `schema: "listing-status/1"`: `source,
     externalId, url, status (AVAILABLE | SOLD | ENDED | REMOVED), price,
     currency, endsAt, checkedAt`. Survey the sold/ended markers on both
     sites first (a tradera page says "avslutad"; the dba marker is
     unknown; a 404 is REMOVED) and record them in the doc. The spiders
     write `<source>_status_<stamp>.jsonl.part`, `run.sh` renames it. On
     the Java side a `ListingStatusParser` + `ListingStatusService.apply`:
     AVAILABLE -> ACTIVE, `last_seen_at` touched, re-indexed when it was
     STALE; SOLD / ENDED / REMOVED -> GONE and the document removed; a
     price change updates the row. The import flow accepts
     `^(dba|tradera)_status_\d{12}\.jsonl$` and routes it to this service.
  5. Scheduling the crawl: `crawler/systemd/announce-crawler.service` +
     `.timer` (user unit, daily 05:00, `MAX_PAGES=10`) and
     `announce-recheck.timer` (every 6 h, both status spiders); `run.sh`
     gains `./run.sh status` for the recheck pass. `scripts/start-stack.sh`
     warns when the newest feed in `done/` is older than
     `max-feed-age`. Document both in the runbook (§11).
  6. Customer SPA (`gateway-api`): `AnnounceCard` for `source != local`
     links to the SPA route `/listing/{id}` (no new tab); new page
     `modules/listing/ListingDetail.tsx` reads `GET /api/listings/{id}`
     (new public read in `listing.interfaces.rest`, the row without
     `quality_issues`, `click_count` and the index columns; edge route
     next to `/api/announces/**`) and shows image, title, brief, price,
     attributes, city, source domain and an "Open at dba.dk" button that
     goes to `/out/{id}` in a new tab; for a GONE row it shows "This ad is
     no longer available at its source" with a link back to the search
     with the same category. i18n `en`, `da`, `sv`. Search facet `status`
     is not added: a non-ACTIVE row is simply not indexed.
  7. Docs: `doc/crawled-listings.md` gains the STALE state, the index
     bookkeeping, the status feed (`listing-status/1`), the systemd units
     and the SPA page; `doc/frontend-edges.md` §3 the two routes.
- **Out of scope:** Kafka transport, image mirroring, geocoding,
  jobs/events from crawled sources, a Java page parser.
- **Acceptance:** `mvn -pl db-reactive test` and
  `mvn -pl announce-management -P '!webapp' test` pass with
  `announce.search.mode=noop`; `pytest` in `crawler/` passes offline with
  fixtures of an available, a sold and a removed item page per site. Tests:
  a fake indexer that fails once leaves `PENDING_REMOVE`, the reconcile job
  retries and the state ends `NONE`; a row not seen for `stale-after` is
  STALE, out of the index and `/out/{id}` still 302s; a `listing-status/1`
  line SOLD sets GONE, removes the document and `/out/{id}` answers 410;
  AVAILABLE on a STALE row re-indexes it; an item with `crawledAt` older
  than `max-feed-age` is stored STALE with no index call; `dba_cars.json`
  in the import dir is ignored; the inventory reports index_state counts.
  Real run: `./run.sh` then `./run.sh status` on the live stack: the
  inventory shows ACTIVE rows, `INDEXED` equals the indexable count, the
  search page lists them, a card opens `/listing/{id}`, "Open at ..."
  lands on the live ad, and a row made GONE by hand shows the "no longer
  available" page. Recorded in `doc/frontend-edges.md` §7.

### Worktree: `auction-schedule` — auction start and end on the announce (2026-10-25)

- **Prerequisite:** nothing in flight; branch off `announce-jo-branch` (the
  `order-reactive` worktree holds the auction history). Read
  `doc/order-context.md` §4, `doc/announce-management-context.md` (goods
  kind), `order/.../auction/infrastructure/messaging/AuctionMessaging.java`
  (`auctionAnnounceEvents`), `AuctionRules`, `AuctionPanel.tsx` and
  `AnnounceDetail.tsx`. Facts: nothing on the announce says when its
  auction runs; the order module opens the auction on `AnnouncePublishedV1`
  with start = `publishedAt` and end = start + `auction.default-duration`
  (7 d) capped by `expiresAt`. `GET /api/auctions/{announceId}` is 404
  until that row exists, and with `auction.seller-account.required=true`
  OpenAuction is refused while the seller has no enabled payout account, so
  the panel shows "The auction is not open yet." with no date at all.
  `announce_auction.start_time` and `AuctionRow.startTime` exist but are
  never rendered; a not-yet-started auction (status OPEN, `isAuctionOpen`
  false) renders "ended {endTime}". `AuctionRules` already refuses a bid
  before `startsAt` (`auction-not-started`). Local auction documents carry
  no `ends_epoch`; crawled tradera auctions do.
- **Decisions:** the seller schedules the auction on the announce (start
  and end, both optional, defaults = publish time and 7 days capped by the
  expiry); the announce is the single source of the schedule and the public
  event carries it; the order module opens at those dates and keeps today's
  rule as the fallback for events without them; the SPA renders three time
  states (before start, open, closed) and shows the planned schedule even
  when no auction row exists yet.
- **Task:**
  1. announce-management domain: `GoodsDetails` gains `auctionStartsAt`,
     `auctionEndsAt` (LocalDateTime, nullable). Rules in `AnnounceRules`:
     only with sale type AUCTION (else `auction-schedule-not-allowed`);
     start < end; end <= `expiresAt` when both are set; at publish, a null
     start becomes `publishedAt` and a null end becomes start +
     `announce.auction.default-duration` (7 d) capped by `expiresAt`, and
     an end in the past is rejected (`auction-already-over`). PostAnnounce
     and EditAnnounce accept the fields; `AnnouncePosted`, `AnnounceEdited`
     and `AnnouncePublished` carry them (publish writes the resolved
     values). Read model: `announce_goods_detail.auction_starts_at`,
     `auction_ends_at` (`announce_table.sql`, build script, mapper regen);
     the projection writes them, `AnnounceBrowsing` reads them.
  2. API module: `GoodsDto` gains `auctionStartsAt`, `auctionEndsAt`;
     `AnnounceSummaryResponse` gains `auctionEndsAt`; `AnnouncePublishedV1`
     and `AnnounceEditedV1` gain `auctionStartsAt`, `auctionEndsAt`
     (nullable, additive, older consumers ignore them). The search
     document (`AnnounceDocument.of`) sets `ends_epoch` and a new
     `starts_epoch` (indexer yml: long, Result + Sort) for local auctions
     so cards and the auction-end sort work across sources.
  3. order: `auctionAnnounceEvents` uses the event's dates when present,
     today's rule otherwise; `AnnounceEditedV1` with changed dates before
     the first bid reschedules (new command `RescheduleAuction`, refused
     once a bid exists or the auction is closed; event
     `AuctionRescheduled`, projection updates `start_time`/`end_time`,
     `AuctionClosingJob` unaffected since it reads the row). Relay a
     `AuctionRescheduledV1` to `auction.v1`.
  4. Customer SPA (`gateway-api`): post wizard goods form shows start and
     end pickers when AUCTION is chosen, `validation.ts` mirrors the rules
     (i18n `en`, `da`, `sv`); `AnnounceDetail` shows an "Auction: starts
     {date}, ends {date}" line from the announce for every AUCTION goods
     announce; `AuctionPanel` renders by time: before `startTime` "starts
     {date}" with the bid form disabled, open "started {date} · ends in
     {time}", closed "ended {date}"; the 404 state shows the announce's
     schedule plus "bidding opens once the seller's payout account is
     enabled" (wording in `trade.json`); `AnnounceCard` shows "Auction ·
     ends {date}" for local auctions as it does for crawled ones.
  5. Admin SPA (`gateway-admin`): the auctions list and detail show start
     and end (`trade` screens; check first, they may already).
  6. Docs: `doc/order-context.md` §4 (opening and rescheduling rule),
     `doc/announce-management-context.md` (goods fields and rules),
     checklist entry in `doc/frontend-edges.md` §7. Session report PDF.
- **Out of scope:** reserve and buy-now prices on the announce, auction
  templates, time zone handling beyond the server's local time, changing
  `auction.default-duration` semantics, notifications about the auction.
- **Acceptance:** `mvn -pl db-reactive test`, `mvn -pl
  announce-management-api,announce-management -P '!webapp' test` and
  `mvn -pl order -P '!webapp' test` pass. Tests: an AUCTION announce
  posted with an explicit schedule publishes with the dates in
  `AnnounceResponse`, in the read model and on `AnnouncePublishedV1`; one
  posted without them gets publish time + 7 d capped by the expiry; end
  before start, a schedule on FIXED_PRICE, and an end in the past are
  rejected with the codes above; order: `AnnouncePublishedV1` with dates
  opens the auction at those dates, without them at the fallback,
  `AnnounceEditedV1` before a bid reschedules and after a bid is refused.
  `npm test` in both SPAs: `AuctionPanel` renders the three time states
  and the 404 state with the planned dates; the wizard refuses end before
  start. Manual: post an auction starting tomorrow, publish, open the
  detail and see the start date; log in as another member and see the
  disabled bid form with "starts ..."; recorded in `doc/frontend-edges.md`
  §7.

### Worktree: `chat-notifications` — new-message notifications for members and staff (2026-10-25)

- **Prerequisite:** nothing in flight; branch off `announce-jo-branch` (the
  `messaging` worktree holds the chat history). Read
  `doc/messaging-context.md` §5, `doc/frontend-edges.md` §14 and §15,
  `messaging/.../infrastructure/live/{LiveSessions,LiveEventsSubscriber,
  LiveFrames}.java`, `application/live/MessageLive.java`,
  `interfaces/ws/*`, `gateway-api/.../modules/chat/{Inbox,Thread,
  useConversationSocket}`, `gateway-admin/.../modules/chat/{useSocket,
  SupportQueuePage}`. Facts: a live frame reaches only the sockets attached
  to that conversation (`LiveSessions.toConversation`) and the staff
  channel `/ws/staff` carries bulletins only; the member inbox polls every
  30 s and shows the per-row `unread` (messages with `seq >
  last_read_seq`, from `announce_conversation_participant` against
  `announce_conversation.last_message_seq`); the header has no badge and
  nothing happens on another page; the staff queue polls WAITING every
  15 s and a staff member learns of a new message in a joined conversation
  only with that thread open. `MessageLive` carries conversationId,
  messageId, seq, senderType, senderId, contentType, content; every node
  consumes `messaging.live`. `doc/messaging-context.md` lists push
  notifications as out of scope; this block adds in-app notifications.
- **Decisions:** in-app only (no mail, no web push); one per-user channel
  over the existing fan-out, derived from the live records, no new events
  in the aggregate; counts come from the read model; the browser
  Notification API is an opt-in extra; the sender never notifies itself.
- **Task:**
  1. messaging, live fan-out: `LiveSessions.attachUser(type, id)` /
     `detachUser` / `toUser(type, id, json)`. `LiveEventsSubscriber` on
     `MessageLive` additionally pushes an `activity` frame `{type:
     "activity", conversationId, conversationType, announceId, subject,
     messageId, seq, senderType, senderId, contentType, preview (120
     chars, empty for OFFER/IMAGE), at}` to every participant of the
     conversation but the sender (participants from the read model through
     `ConversationQueries`, one IN query cached 60 s per conversation as
     the state cache does); on `ConversationStatusLive` JOINED it pushes
     `activity` to the member whose support conversation staff joined. A
     new `QueueLive` record (relayed like `BulletinLive` from
     `ConversationOpened` of type SUPPORT) is pushed as `{type: "queue",
     conversationId, subject, memberId, at}` to every staff session.
  2. messaging, endpoints: WebSocket `/ws/me` (`MeWebSocketHandler`,
     member or staff from `X-User-Id`/`X-User-Type` as the other handlers,
     staff through `Principals.staff`; client sends only keep-alives;
     lifecycle via `SocketLifecycle`); `GET /api/conversations/unread` ->
     `{conversations, messages}` for the caller (sum over its participant
     rows of `last_message_seq - last_read_seq`, excluding CLOSED/BLOCKED);
     `GET /api/admin/conversations/summary` -> `{waiting, myUnread}`.
     `/ws/staff` keeps bulletins and now also receives `queue` frames, so
     the admin SPA needs one socket, not two.
  3. Edges: `gateway-api` route `messaging-ws` gets
     `Path=/ws/conversations/**,/ws/me` (token via the existing
     `WebSocketTokenFilter`); `/api/conversations/**` already covers the
     count. `gateway-admin` `/ws/**` and `/api/admin/conversations/**`
     already route; nothing new.
  4. Customer SPA (`gateway-api`): `notificationsSlice` (unread counts,
     last 20 activity items, browser-notification opt-in); `useMeSocket`
     opened once in `Layout` while authenticated and `features.chat` is
     on (reconnect/backoff as `useConversationSocket`, falls back to
     polling `GET /api/conversations/unread` every 60 s when the socket is
     unavailable); header: badge with the unread conversation count on
     Messages; `ToastContainer` bottom-right showing an activity toast
     (sender, preview, click opens the thread) unless that thread is on
     screen; document title prefixed "(n) " while n > 0; `Inbox` rows
     update from frames (unread + preview) and `Thread`'s mark-read
     refetches the count; opt-in toggle "Desktop notifications" on the
     inbox (permission requested on click only, choice in localStorage,
     `new Notification(...)` only when the tab is hidden). i18n `en`,
     `da`, `sv`.
  5. Admin SPA (`gateway-admin`): the existing `/ws/staff` socket moves
     from `BulletinsPage` to the shell (one per session, `useSocket`),
     dispatching `bulletin`, `queue` and `activity` frames to a
     `notificationsSlice`; sidebar badge on "Support queue" with the
     WAITING count (`summary` on load, +1 on `queue`), toast on `queue`
     and `activity` (click opens the conversation); `SupportQueuePage`
     refetches on a `queue` frame in addition to its poll. i18n `en`,
     `da`, `sv`.
  6. Docs: `doc/messaging-context.md` §4 (two endpoints) and §5 (`/ws/me`,
     the `activity` and `queue` frames, at-least-once as today),
     `doc/frontend-edges.md` §14/§15 gain the badge and toast behaviour
     and a §7 checklist entry. Session report PDF.
- **Out of scope:** mail and web push (service worker), notification
  preferences beyond the one toggle, per-message read receipts, muting a
  conversation, notifications for order events (their SYSTEM messages
  already produce `activity` frames), mobile.
- **Acceptance:** `mvn -pl messaging test` passes (Testcontainers + live
  MySQL as today): two members, one posts, the other's `/ws/me` receives
  exactly one `activity` frame and the sender none; a third member gets
  nothing; a SUPPORT open pushes `queue` to a staff `/ws/staff` socket; a
  staff join pushes `activity` to the member; `GET
  /api/conversations/unread` counts match the inbox rows and drop to zero
  after mark-read; `/ws/me` refuses a handshake without `X-User-Id`.
  `mvn -pl gateway-api -P '!webapp' test` covers the `/ws/me` route.
  `npm test` and `npm run i18n:check` pass in both SPAs: header badge from
  the count endpoint, toast on an `activity` frame and none for the open
  thread, title prefix, admin sidebar badge from `summary` and `queue`.
  Manual on the stack: two browsers, member A writes to member B on
  another page -> B sees the badge and the toast within a second and the
  thread opens from it; a member opens a support conversation -> the
  staff sidebar badge increments and a toast appears; recorded in
  `doc/frontend-edges.md` §7.
