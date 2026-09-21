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
