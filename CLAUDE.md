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
