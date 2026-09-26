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
- Modules that compile: config, eureka, gateway, db-reactive, cqrs-reactive,
  announce-management. core, auth-server and order still carry legacy Boot 2 code.
- Tests in db-reactive and announce-management need the live MySQL database
  `announce_reactive_db` (user `announce`) on localhost:3306. Never touch the
  `litemall` database on the same server.
- `java` on PATH is 11; Maven runs on `/usr/lib/jvm/java-21-openjdk-amd64`.
- Design notes: `doc/announce-management-context.md`, `doc/auction-chat-schema.md`.

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
     discriminator in the body and filters by kind on listing; keep
     `X-Seller-Id` as the owner header.
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
     `X-User-*`, injects `X-User-Id`, `X-User-Type=MEMBER`, and
     `X-Seller-Id` on announce-management routes; `machine/MachineTokenRelayFilter`
     adds `X-Service-Token`.
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
  OK, write without token 401, valid token -> `X-User-Id` and
  `X-Seller-Id` downstream, spoofed `X-User-Id` dropped, SPA fallback,
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
