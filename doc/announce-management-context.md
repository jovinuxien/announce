# What belongs in the announce-management service

Sources: *Domain-Driven Design Distilled* (Vernon, 2016), chapters 2, 3 and 5;
*Patterns, Principles, and Practices of Domain-Driven Design* (Millett & Tune, 2015), chapters 1, 2, 5 and 6.
Applied to the 39 tables of `announce_reactive_db` and the 33 reactive mappers in `db-reactive`.

## 1. The rules the books give us

1. **A bounded context is a linguistic boundary first** (Millett ch. 6, Vernon ch. 2). When the same word means
   different things to different people, split the model. "Goods" means one thing to the member posting an announce
   (title, pictures, price, category), another to the buyer's order (a line with a snapshot of name and price),
   another to the auction (an item id with a current price), another to search (an index document).
   Millett's figure 6-9: *one Product class per context, never one shared Product for all of them.*
2. **Align contexts with business capabilities**, but check them against the language (Millett ch. 6).
   For a classifieds site the capabilities are: publish and manage announces, find announces, buy or bid,
   pay, talk to the counterparty, manage your account, run the site.
3. **Classify subdomains** (Vernon ch. 3): *core* is where the business must excel and invests most;
   *supporting* is custom but not distinguishing; *generic* could be bought or outsourced. One bounded context per
   subdomain. For a Blocket / DBA / Tradera style site, the core is the announce lifecycle and the marketplace
   mechanics around it (auction, trade), not user accounts or file storage.
4. **Challenge and unify** (Vernon ch. 2, page 29): ask of every concept "does it adhere to the ubiquitous language
   of this context?" Tenant, User, Permission have nothing to do with Scrum and were factored out of the Scrum
   core. The same test removes user, admin, role, permission, log, storage and system from the goods context.
5. **Aggregate rules** (Vernon ch. 5): protect invariants inside the boundary; design small aggregates;
   reference other aggregates by identity only; update other aggregates with eventual consistency (domain events).
   This decides which tables cluster into one aggregate and which stay separate.
6. **Your domain model is not your enterprise model** (Millett fig. 6-7): reporting joins across contexts belong to
   a separate reporting model, not inside a context.

## 2. Applying rule 1 and 2: the contexts of the announce application

| Subdomain (type) | Bounded context / module | Capability |
|---|---|---|
| Announce catalog (**core**) | **announce-management** | a member creates, edits, publishes, expires and withdraws announces |
| Auction (**core**) | auction (new) | timed bidding, snag-it, closing, winner |
| Order and checkout (supporting) | order | cart, order, order lines, fulfilment, optimistic lock |
| Payment (supporting / generic) | payment | tokenised billing info, PSP integration |
| Messaging and support (supporting) | messaging (new) | buyer-seller chat, member-staff chat, bulletins, feedback, FAQ |
| Search and discovery (supporting) | search (litemall-elastic) | index of announces, keywords, search history |
| Marketing (supporting) | marketing (new or inside admin) | ads, topics, featured announces |
| Member and reputation (supporting) | member (new) | profile, addresses, watch list, browsing history, trade ratings, tiers |
| Identity and access (**generic**) | auth-server | admins, roles, permissions, audit log |
| Notification (generic) | notification (new, or part of messaging) | notices to members and staff |
| Platform (generic) | config / shared | regions, storage, system settings |
| Reporting (enterprise model) | admin dashboard | daily statistics across contexts |

## 3. Tables and mappers that belong to announce-management

Applying rule 5 (small aggregates, reference by id) inside the announce catalog:

| Aggregate | Root table | Child tables (same transaction) | Mappers | Notes |
|---|---|---|---|---|
| **Announce** (aggregate root) | `announce_goods` | `announce_goods_product` (variants: price, stock, pictures), `announce_goods_attribute` (name/value facts), `announce_goods_specification` (selectable options) | `LitemallGoodsMapper`, `LitemallGoodsProductMapper`, `GoodsProductMapper` (stock), `LitemallGoodsAttributeMapper`, `LitemallGoodsSpecificationMapper` | Invariants: an announce is published only with a title, a category, a price or an auction; a variant's stock never goes negative (`reduceStock` already refuses); `sale_type` decides fixed price vs auction; `seller_id` is the owner. Category and brand are referenced by id. |
| **Category** | `announce_category` | none (tree via `pid`) | `LitemallCategoryMapper` | Own aggregate: it changes for its own reasons (site taxonomy), not when an announce changes. |
| **Brand** (manufacturer) | `announce_brand` | none | `LitemallBrandMapper` | Own aggregate; optional reference data for an announce. |

That is 6 tables and 8 mappers. The existing `announce-management` module already declares exactly these six entities
(`AnnounceGoodsEntity`, `AnnounceGoodsProductEntity`, `AnnounceGoodsAttributeEntity`,
`AnnounceGoodsSpecificationEntity`, `AnnounceCategoryEntity`, `AnnounceBrandEntity`), so the module's current
scope already matches the books' guidance. What it should **not** grow to include is listed in section 5.

### Candidate application services (use cases) for announce-management

Millett (ch. 2, page 22) warns against `XxxService` and `XxxManager` names that hide intent. Name the services after
what a member or staff member does:

| Service / use case | Aggregate | Domain event published (rule 5, eventual consistency) |
|---|---|---|
| `PublishAnnounce`, `EditAnnounce`, `WithdrawAnnounce`, `ExpireAnnounces` (scheduled) | Announce | `AnnouncePublished`, `AnnounceUpdated`, `AnnounceWithdrawn`, `AnnounceExpired` |
| `ChangeAnnouncePrice`, `MarkAnnounceSold` | Announce | `AnnouncePriceChanged`, `AnnounceSold` |
| `AddVariant`, `AdjustStock` (uses `addStock` / `reduceStock`) | Announce | `StockAdjusted`, `VariantSoldOut` |
| `BrowseAnnounces` (by category, brand, seller, keywords, paged) | read side | none (query) |
| `ManageCategoryTree` (create, rename, move, hide) | Category | `CategoryChanged` |
| `ManageBrands` | Brand | `BrandChanged` |

Who consumes the events: search (re-index), auction (an auction is opened when an announce with
`sale_type = AUCTION` is published), order (reserve stock when an order is placed, release on cancel), marketing
(a featured announce that expires is dropped), messaging (a listing chat is closed when the announce is sold).

## 4. Tables that reference goods but belong elsewhere (rule 1: different meaning, different model)

| Table | Context | What "goods" means there |
|---|---|---|
| `announce_order_goods` | order | an order line: a *snapshot* of name, price, picture and specification at purchase time (already denormalised) |
| `announce_cart` | order | a basket line: goods id, product id, quantity, price at the time it was added |
| `announce_auction`, `announce_bid`, `announce_auction_event` | auction | the item under auction: goods id plus current price and bids |
| `announce_comment` (`value_id`, `type`) | member / reputation | a review left by a member about a goods or a topic |
| `announce_collect` | member | a watch-list entry: goods id only |
| `announce_footprint` | member | a browsing-history entry: goods id only |
| `announce_topic`, `announce_ad`, `announce_featured_item` | marketing | editorial placements holding goods ids |
| `announce_keyword`, `announce_search_history`, `LitemallElasticMapper` join | search | index documents and query terms; the join is a read model built from goods, category and brand |
| `announce_conversation` (`goods_id`) | messaging | the announce a chat is about, by id |
| `announce_trade_rating` | member / reputation | rating of the counterparty per order |

Each of these keeps a `goods_id` and copies the few fields it needs. None of them should import the announce-management
entity classes; they subscribe to its events or call its published API (open host service + published language,
Vernon ch. 4).

## 5. Tables with no place in announce-management (rule 4: not in the language)

| Tables | Context |
|---|---|
| `announce_user`, `announce_address`, `announce_billing_info` | member / payment |
| `announce_admin`, `announce_role`, `announce_permission`, `announce_log` | identity and access (auth-server) |
| `announce_notice`, `announce_notice_admin` | notification |
| `announce_feedback`, `announce_issue`, `announce_bulletin`, `announce_chat_message`, `announce_conversation_participant` | messaging and support |
| `announce_region` | platform reference data (used by address and, later, by an announce's location) |
| `announce_storage`, `announce_system` | platform |
| `StatMapper` (daily users, orders, goods) | reporting / enterprise model |

## 6. Consequences for the migration

* The legacy `LitemallGoodsService` (now in `db-reactive/legacy/service`) mixes catalog queries with PageHelper
  paging and the elastic join. Port only the catalog behaviour into announce-management; the search join goes to the
  search context and the paging is already explicit in the mappers.
* `LitemallElasticDataService` reads goods, category and attribute to build index rows. That is the search
  context's anti-corruption layer over the goods published language, not a goods service.
* The announce-management module today depends on `core` and pulls `spring-webmvc`. Rule 3 of Vernon (reference by
  identity only) and Millett's border-control note argue for the module to depend on `db-reactive` for its six
  mappers and on nothing else from the old monolith.
* Two open questions for the domain expert (you): does a classifieds announce need variants and stock at all,
  or is `announce_goods_product` a mall leftover to be simplified into "one item, one price, quantity 1"?
  And should an announce carry a location (region id) in the catalog, as Blocket and DBA do?

## 7. Implementation status (2026-09-20)

Decisions taken: an announce **carries a location** (`Location` value object: region id from `announce_region`, city, postal
code, optional coordinates; at least one of the first three is required to publish). Variants and stock are kept for now.

Built in `announce-management` (package `org.linlinjava.announce.goods`):

| Layer | Classes |
|---|---|
| domain.announce | `Announce` (aggregate root), `Variant`, `Attribute`, `Specification`, `AnnounceDetails`, `Location`, `Money`, `SaleType`, `AnnounceStatus`, `AnnounceId`, `SellerId`, `AnnounceQuery`, `AnnounceSummary`, `AnnounceRepository`, events (`AnnouncePublished`, `AnnounceUpdated`, `AnnouncePriceChanged`, `AnnounceWithdrawn`, `AnnounceSold`, `AnnounceExpired`, `StockAdjusted`) |
| domain.category / domain.brand | `Category` + `CategoryRepository` + `CategoryChanged`; `Brand` + `BrandRepository` + `BrandChanged` |
| application | `AnnouncePublishing` (draft, publish, edit, changePrice, withdraw, markSold, expireDue), `AnnounceStock` (addVariant, adjustStock), `AnnounceBrowsing` (view, browse, count), `CategoryTreeManagement`, `BrandManagement`, commands in `AnnounceCommands` |
| infrastructure | `MyBatisAnnounceRepository`, `MyBatisCategoryRepository`, `MyBatisBrandRepository` over the db-reactive mappers; `SpringDomainEventPublisher`; `AnnounceExpiryJob` (cron, `announce.expiry.*`); `CatalogSecurityConfig` (gateway-trusting, see class comment) |
| interfaces.rest | `/api/announces`, `/api/categories`, `/api/brands`, `CatalogExceptionHandler` (RFC 7807) |

Schema additions (in `announce_auction_chat.sql`, section 4): `announce_goods.status` (DRAFT, PUBLISHED, SOLD, WITHDRAWN, EXPIRED),
`region_id`, `city`, `postal_code`, `latitude`, `longitude`, plus indexes on status/expiry, region and seller.
The goods mapper gained these columns and a hand-written `updateAnnounce` statement that rewrites exactly the aggregate's columns.

## 8. Announce kinds and the read model (2026-09-22)

The Announce aggregate became a generic announce with three kinds, event-sourced on OpenCQRS through the
`cqrs-reactive` framework (`doc/cqrs-reactive.md`). The `db-reactive` tables are now read models fed by projections.

### The aggregate

`Announce` (`domain.announce`) is an immutable record rebuilt from its events under subject `/announce/{id}`: owner
(`SellerId`), `AnnounceContent` (title, brief, description, keywords, gallery, category, `Location`), a sealed
`AnnounceKind`, status, `publishedAt`, `expiresAt`. Ids are UUID strings chosen by the command side (the read
tables carry `varchar(36)`; migrated seed rows keep their numeric ids as strings).

| kind | details record | specific fields | biddable | ends as |
|---|---|---|---|---|
| GOODS | `GoodsDetails` | price (`Money`), condition, `SaleType` (FIXED_PRICE, AUCTION, GIVEAWAY), brand, unit, variants, attributes, specifications | yes (AUCTION) | SOLD, WITHDRAWN, EXPIRED |
| JOB | `JobDetails` | employer, contract type, optional `SalaryRange`, application deadline, how to apply | never | WITHDRAWN, EXPIRED |
| EVENT | `EventDetails` | start and end, venue (free text; the location gives region and city), organizer, free or ticketed, capacity | never | WITHDRAWN, EXPIRED |

The invariants live in `AnnounceRules` and answer with RFC 7807 problems carrying a stable `code`:

| rule | code | when |
|---|---|---|
| a job or event cannot have an auction sale type | `not-biddable` | post, edit |
| the kind never changes | `kind-immutable` | edit |
| a location is required | `location-required` | publish |
| a fixed-price goods announce needs a price (own or on a variant) | `price-required` | publish |
| an event starts before it ends, and the end is in the future | `event-dates`, `event-in-the-past` | publish |
| a job whose deadline has passed is not published | `deadline-passed` | publish |
| SOLD applies to goods only | `sold-not-applicable` | mark sold |
| only the owner writes | `not-owner` (403) | every member command |

Commands (`AnnounceCommands`): `PostAnnounce`, `EditAnnounce`, `PublishAnnounce`, `WithdrawAnnounce`, `MarkSold`,
`ExpireAnnounce` (system, idempotent). Events, one top-level record each in `domain.announce.events`:
`AnnouncePosted`, `AnnounceEdited`, `AnnouncePublished`, `AnnounceWithdrawn`, `AnnounceSold`, `AnnounceExpired`.
`AnnounceHandlers` holds the explicit OpenCQRS definitions; `AnnouncePublishing` checks the references the aggregate
cannot see (a visible category of the same kind, the brand) and dispatches through `ReactiveCommandRouter`, with the
`X-Command-Id` header as idempotency key. Variants and stock are no longer separate commands: they are part of
`GoodsDetails` and change through `EditAnnounce` (stock reservation belongs to the order context).

### Read-model tables (`db-reactive/sql/announce_table.sql`)

| table | key | holds |
|---|---|---|
| `announce` | `id` varchar(36) | kind, seller_id, category_id, title, brief, detail, gallery (JSON), keywords, region/city/postal code/coordinates, status, published_at, expires_at, last_event_id, add/update_time, deleted |
| `announce_goods_detail` | announce_id | price, condition, sale_type, brand_id, unit |
| `announce_job` | announce_id | employer, contract_type, salary_min/max, currency, application_deadline, apply_url_or_email |
| `announce_event` | announce_id | starts_at, ends_at, venue, organizer, ticketed, ticket_price, capacity |
| `announce_goods_product`, `announce_goods_attribute`, `announce_goods_specification` | id, keyed by announce_id | goods children as before |
| `announce_category` | id | gains `kind`: goods, jobs and events have separate trees; a child inherits its root's kind |

The former `announce_goods` table is gone; the auction columns it had gained (seller_id, sale_type, expire_time,
status, location) live in `announce` and `announce_goods_detail`. `announce_auction_chat.sql` no longer alters goods.
The seed (`announce_data_en.sql`) was migrated with `announce_migrate_goods.sql`: every goods row became kind GOODS,
fixed price, owned by member 1, published when it was on sale, without location or expiry. Other contexts keep
their `goods_id int` columns; the migrated ids still match by value.

Mappers: `AnnounceBaseMapper` (table `announce`, with hand-written `upsert`, `updateContent`, `updateStatus` and the
joined `selectSummaries` / `countSummaries` listing query), `AnnounceGoodsDetailMapper`, `AnnounceJobDetailMapper`,
`AnnounceEventDetailMapper` (each with `upsert`). Regenerate with the `mbg` profile of `db-reactive/pom.xml` and
`mybatis-generator/reactify_mapper.py`.

### Projections (`infrastructure.projection`)

| group | projection | writes |
|---|---|---|
| `announce-read-model` | `AnnounceReadModelProjection` | the tables above: `AnnouncePosted` upserts base + detail + children, `AnnounceEdited` rewrites the content columns and the detail, the lifecycle events update status and dates; `last_event_id` records the event applied |
| `announce-search` | `AnnounceSearchProjection` | one document per visible announce to the `AnnounceIndexer` port, which delegates to the shared `org.linlinjava.announce.search.SearchIndexer` (`OcsSearchIndexer` pushes to the OCS indexer of `docker/cqrs`, index `announce_index`, `kind` and `source=local` as facets); removal on withdraw, sold and expire |

Both groups checkpoint in `cqrs_readside_progress` (`R2dbcProgressTracker`) and are at-least-once: every handler is
idempotent. The search projection builds its document from the read model, reading the row once
(`AnnounceReadRepository.findApplied`: state and `last_event_id` from the same snapshot); when the read-model group
has not yet applied the event (`last_event_id` older than the event), the handler fails and the framework retries
it. When the read model is already *ahead* (the announce was sold while the search group was still catching up on
the publish), the document reflects that later state and the intermediate one is skipped: the index converges to the
read model, but not every event produces an index call. Queries
(`AnnounceBrowsing` over `AnnounceReadRepository`) read the tables only, never the event store. The expiry job reads
the due ids from `announce` and sends one `ExpireAnnounce` each.

### REST (`/api/announces`)

The body carries `kind` and one of `goods`, `job`, `event`; listing filters on `kind` (and category, brand, seller,
region, keywords, sale type, sort). `X-Seller-Id` stays the owner header, `X-Command-Id` makes a write idempotent.
Writes answer with the state the command produced (201 on post); reads come from the projection a moment later.
`/api/categories` takes and returns `kind` (`?kind=JOB` lists that tree).

### Tests

`AnnounceTest` drives the aggregate through `CommandFixture`; `AnnounceKindsEndToEndTest` boots the service with
Testcontainers only (EventSourcingDB, MySQL loaded with the schema and the migrated seed), posts and publishes one
announce of each kind, reads the kind-specific fields back from the projection, checks the invariants over HTTP and
the recording indexer of `announce.search.mode=noop`. `mvn -pl announce-management -P '!webapp' test` runs them; `MyBatisRepositoriesIT` (category
and brand against the live database) runs with `-Dtest=MyBatisRepositoriesIT`.

## 9. Crawled listings as a second search source (2026-09-26)

Listings crawled from dba.dk and tradera.com are not announces: they never enter the event store or the `announce*`
tables. The `listing` sub-context (`org.linlinjava.announce.listing`, same module, nothing shared with `goods` but
the `search` package) keeps them in `crawled_listing`, imports the scrapy feeds through a Spring Integration file
flow, indexes them into the same `announce_index` with `source` as a facet, and links out through `GET /out/{id}`.
Design, schema, feed format, index fields and the legal stance (snippet + link, no full text): `doc/crawled-listings.md`.

## 10. Public events: `announce.v1` in, `order.v1` out (2026-09-26)

The service now has a public face over Kafka, built with the `messaging` package of `doc/cqrs-reactive.md` §3. The
contracts live in the module `announce-management-api` (package `org.linlinjava.announce.goods.api`), which consumers
depend on instead of the service itself.

| direction | binding / function | topic | records |
|---|---|---|---|
| out (relay group `announce-public`) | `announce-out-0` | `announce.v1` | `AnnouncePublishedV1` (id, kind, sellerId, title, mainPicture, priceCents, currency, saleType, categoryId, publishedAt, expiresAt), `AnnounceEditedV1` (same snapshot without seller and dates), `AnnounceWithdrawnV1`, `AnnounceExpiredV1`, `AnnounceSoldV1` (id, at) |
| in (function `orderEvents`) | `orderEvents-in-0`, group `announce-management` | `order.v1` (module `order-api`) | `OrderPaidV1` -> `MarkSold` on behalf of the seller; `OrderCancelledV1` and `OrderRefundedV1` -> `RelistAnnounce` |

Two changes to the aggregate made this a pure mapping:

- `AnnouncePublished` carries the owner, content and details as they were at publication, so the relay builds the
  public record from the event alone (no read-model lookup on the relay thread). Prices travel in minor units with
  the site currency, `announce.currency` (default `DKK`), because `Money` has no currency of its own.
- A new system command `RelistAnnounce(id)` turns a SOLD announce back into PUBLISHED when the deal behind the sale
  falls through, keeping the expiry when it is still ahead (else the default validity). Any other status is left
  untouched, so the command is idempotent. `AnnounceStatus.canBePublished()` is unchanged: the owner still cannot
  republish a sold announce by hand.

The subscriber uses the record's `event-id` as command id (`mark-sold:<id>`, `relist:<id>`), so a redelivered record
replays the stored result. A paid deal on an announce that is no longer PUBLISHED (sold by hand, withdrawn, unknown)
is logged and skipped rather than retried: the order module owns that inconsistency, not this service.

`AnnounceKindsEndToEndTest` gained the Kafka container: it probes `announce.v1` and injects `order.v1` records with
the test kit's `PublicEventTestProducer`, standing in for the order module.
