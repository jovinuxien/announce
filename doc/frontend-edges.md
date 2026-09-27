# The two frontend edges: `gateway-api` (customers) and `gateway-admin` (staff)

Status: design written 2026-09-26; `gateway-api` implemented 2026-09-26 (section 9 records what was
built and the checklist run), `gateway-admin` not yet. Worktrees `gateway-api` and `gateway-admin` in
`CLAUDE.md`. Model: litemall's `litemall-gateway-api` and
`litemall-gateway-admin` (`~/shopping_apps/litemall_app/litemall`), adapted to the announce
backend (`doc/announce-management-context.md`, `doc/crawled-listings.md`,
`doc/order-context.md`).

## 1. Shape

Each edge is one Maven module that is both the API gateway and the host of one single-page
application, exactly like litemall:

| | `gateway-api` | `gateway-admin` |
|---|---|---|
| Audience | members and visitors (buy, sell, search, chat) | staff (moderation, catalog, listings, orders, support) |
| Port | 8080 | 8081 |
| Java package | `org.linlinjava.announce.gatewayapi` | `org.linlinjava.announce.gatewayadmin` |
| Gateway | Spring Cloud Gateway (WebFlux), Eureka `lb://` routes, explicit routes only | same |
| SPA | `src/main/webapp`, npm package `@announce/customer-web` | `src/main/webapp`, `@announce/admin-web` |
| SPA stack | React 18, TypeScript 5, react-router 6, Redux Toolkit, axios, react-bootstrap 5, i18next (en, da, sv), webpack 5, jest | same |
| Build | `frontend-maven-plugin` in the `webapp` Maven profile (node v20.16.0 / npm 10.8.1 as in announce-management); `-P '!webapp'` skips it; `npm run webapp:dev` proxies `/api`, `/auth`, `/out`, `/ws` to the edge | same, proxy to 8081 |
| Identity | self-issued RS256 JWT for members over `announce_user` | self-issued RS256 JWT for staff over `announce_admin` + roles |

The existing `gateway` module (port 9001, routes `/goods/**` plus litemall's `/wx/**` and
`/admin/**`) is replaced by `gateway-api` and deleted in that worktree. `auth-server`
(legacy Boot 2) stays untouched and unused; an external identity provider can replace the
self-issued tokens later behind the same `IdentityForwardingFilter`.

## 2. Conventions shared by both edges

- **Deny by default.** `SecurityConfig` requires a valid Bearer token on every exchange except
  what `PublicPaths` enumerates. CSRF off (stateless JSON). The SPA keeps the access token in
  memory and the refresh token in an httpOnly cookie scoped to `/auth`.
- **No StripPrefix.** Backends keep their `/api/...` paths; routes are matched in declaration
  order, specific prefixes first.
- **Identity forwarding.** The edge strips every client-supplied `X-User-*` header, validates
  the token, and injects `X-User-Id`, `X-User-Type` (`MEMBER` or `ADMIN`) and, on the admin
  edge, `X-User-Roles`. On routes to announce-management the customer edge also sets
  `X-Seller-Id` to the same value until announce-management reads `X-User-Id` (follow-up in
  that module, then drop the duplicate).
- **Machine token.** `MachineTokenRelayFilter` adds `X-Service-Token` (HMAC over a shared
  secret from config) on every `lb://` route so backends can later refuse traffic that did not
  pass an edge. Backends verify it in a later step; today they trust the headers (see
  `CatalogSecurityConfig`).
- **SPA history fallback.** A `WebFilter` rewrites every GET whose last path segment has no
  dot and that matches no route or public asset to `/index.html`, so deep links work.
- **Problem details pass through.** Backend `application/problem+json` bodies reach the SPA
  unchanged; the edge only maps its own auth failures (401 / 403) the same way.
- **Refresh tokens** live in a new table `announce_refresh_token` (`id`, `user_type`,
  `user_id`, `token_hash`, `expires_at`, `revoked`, `add_time`) in `db-reactive`, reactive
  mapper generated like the others.
- **Local dev.** Both edges register with Eureka and read the config server like the other
  modules. `docker/docker-compose.yml` gains the two edges once they build.

## 3. Customer edge routes (`gateway-api`, 8080)

| Path | Target | Auth |
|---|---|---|
| `/auth/login`, `/auth/register`, `/auth/refresh`, `/auth/logout`, `/auth/me` | local `AuthController` | public (`/auth/me` member) |
| `GET /api/announces/**`, `GET /api/categories/**`, `GET /api/brands/**` | `lb://announce-management` | public |
| `POST|PUT /api/announces/**` | `lb://announce-management` | member |
| `GET /out/**` | `lb://announce-management` | public (302 to the source listing) |
| `GET /api/search` | `http://searcher:8534`, `SetPath` to the OCS search API for tenant `announce_index` (`docker/cqrs`) | public |
| `GET /api/suggest` | `http://suggest:8536`, `SetPath` to the OCS suggest API for `announce_index` | public |
| `/api/auctions/**`, `/api/orders/**`, `/api/sellers/**` | `lb://order` | member |
| `POST /api/payments/webhook/**`, `POST /api/shipping/webhook/**` | `lb://order` | public (provider-signed; the service verifies) |
| `/api/conversations/**` | `lb://messaging` | member |
| `/ws/conversations/**` | `lb:ws://messaging` | member (token as `access_token` query parameter at handshake, injected as headers) |
| everything else | SPA fallback | public |

The search response is the OCS searcher JSON as is; the SPA's search module adapts it
(litemall's `modules/search/instantsearch` is the starting point).

## 4. Admin edge routes (`gateway-admin`, 8081)

| Path | Target | Auth |
|---|---|---|
| `/auth/**` | local admin `AuthController` | public |
| `/api/admin/listings/**` | `lb://announce-management` (exists: import, reindex) | ROLE_ADMIN |
| `/api/admin/announces/**` | `lb://announce-management` (new small controller: list all with filters, withdraw, expire, no owner header) | ROLE_ADMIN |
| `POST|PUT|PATCH|DELETE /api/categories/**`, `/api/brands/**` and their GETs | `lb://announce-management` | ROLE_ADMIN |
| `/api/admin/orders/**`, `/api/admin/bids/**`, `/api/admin/payouts/**` | `lb://order` | ROLE_ADMIN |
| `/api/admin/conversations/**`, `/api/admin/bulletins/**` | `lb://messaging` | ROLE_ADMIN |
| `/api/admin/users/**`, `/api/admin/admins/**`, `/api/admin/roles/**`, `/api/admin/permissions/**`, `/api/admin/regions/**`, `/api/admin/dashboard` | edge-hosted reactive controllers over `db-reactive` mappers (litemall's `EdgeAdmin*` pattern; there is no member context yet) | ROLE_ADMIN, `ROLE_SUPER` for admins and roles |
| everything else | SPA fallback | public |

Edge-hosted admin CRUD moves out to a member context when one exists; the SPA talks to the
same paths either way.

## 5. Customer SPA

Phase 1 codes against the backend that exists today (announce-management, crawled listings,
OCS). Phase 2 codes against the REST contracts in `doc/order-context.md` §8 and §9 behind
feature flags (`features.trade`, `features.chat`) with MSW mocks, so the screens are ready
when `order-reactive` and `messaging` land.

Phase 1 modules (`app/modules/*`):

- **shell**: layout, header with kind tabs (Goods, Jobs, Events), search box with suggest,
  language switch, footer; auth guard; error boundary showing problem details.
- **auth**: login, register, logout, refresh on 401, profile.
- **home**: category roots per kind (from `/api/categories?kind=`), latest announces per kind
  (search sorted by `created_epoch`), region chooser.
- **search**: query, facets `kind`, `category_ids`, `price`, `region_id`/`city`, `condition`,
  `sale_type`, `source`, plus job facets (`contract_type`, salary) and event facets (dates),
  sort, pagination, result cards. A card with `source != local` shows the source domain and
  links through `/out/{id}` in a new tab.
- **announce**: detail page with one layout per kind (goods: price, condition, gallery,
  attributes; job: employer, contract, salary, deadline, how to apply; event: dates, venue,
  organizer, tickets), seller box, location map placeholder, share.
- **post**: wizard: kind, category (tree of that kind), kind-specific form (validation mirrors
  the aggregate invariants: event end after start and in the future, goods price unless
  giveaway or auction, no auction for jobs and events), gallery upload (URLs for now),
  location, preview, save draft, publish.
- **my-announces**: drafts, published, sold, withdrawn, expired; edit, publish, withdraw,
  mark sold.

Phase 2 modules (flagged): **auction** panel on a goods detail (current price, bid count, bid
form, snag-it, countdown, my bids), **buy** flow (request purchase, seller accept, pay with
the provider client secret, shipment tracking, confirm receipt, dispute), **orders** (buyer
and seller lists, detail with timeline), **chat** (conversation list, thread over WebSocket,
offer message, accept or decline, system messages).

## 6. Admin SPA

Phase 1: login; dashboard (counts of announces per kind and status, listings per source,
members) from the edge dashboard endpoint; categories per kind (tree, create, rename,
move, show or hide, delete); brands; announces moderation (filter by kind, status, seller,
region, text; detail; withdraw; expire); crawled listings (trigger import for a file,
reindex, counts per source and status, recent GONE rows, click counts); members (list,
search, status); admins, roles and permissions; regions.

Phase 2 (flagged): auctions (list, cancel a bid with reason), orders (list by status,
detail with payment and shipment timeline, resolve dispute to release or refund), payouts
overview, support queue (WAITING conversations, join, bulletins).

## 7. Tests and acceptance

- Edge: `WebTestClient` route tests with MockWebServer as the backend. Public GET passes
  without token; write without token is 401; a valid token yields `X-User-Id` downstream and
  a spoofed client `X-User-Id` is dropped; the SPA fallback answers `/announce/abc` with
  `index.html` and `/app/main.js` with 404 when the asset is missing; `/api/search` reaches
  the searcher path for `announce_index`; the WebSocket route upgrades.
- SPA: jest for the post wizard validation per kind, the search facet state, the auth slice
  and the token refresh interceptor; `npm run i18n:check` passes for `en`, `da`, `sv`.
- Build: `mvn -pl gateway-api -P webapp -DskipTests package` produces a jar that serves the
  SPA; `mvn -pl gateway-api -P '!webapp' test` runs without node.
- Manual end to end, written as a checklist in this document once implemented: register,
  log in, post one announce of each kind, publish, find each in search with its kind facet,
  open a crawled listing and land on the source page; admin: log in, create a category of
  each kind, withdraw an announce, trigger a listings import.

## 8. Out of scope for the first iteration

Image upload to object storage (URLs only), SEO head rendering and sitemap (litemall has
them; port later), Google sign-in, password reset by mail, push notifications, a shared
`edge-common` module (the two edges duplicate the security package as litemall does; extract
when a third edge appears), Caddy or TLS, the OCS admin frontend.

## 9. `gateway-api`: what was built (2026-09-26) and the checklist run

Module `gateway-api` (root pom entry replaces `gateway`, which is deleted). Guide with the same content
as a PDF: `doc/gateway-api-guide.pdf`; module README: `gateway-api/README.md`.

**Edge.** `org.linlinjava.announce.gatewayapi`: `auth` (`AuthController` on `/auth/login|register|refresh|logout|me`
over `announce_user` through the ports `UserAccounts` / `RefreshTokenStore` and their R2DBC adapters; RS256
through Spring Security's Nimbus `JwtEncoder`/`JwtDecoder`, dev keypair in `application.yml`, blank keys mean
an ephemeral pair; refresh token = `<uuid>.<random>` with only the SHA-256 in `announce_refresh_token`, single
use, 30 days, httpOnly cookie `announce_refresh` on path `/auth`, `SameSite=Strict`), `security`
(`SecurityConfig` deny by default with problem-detail 401/403, `PublicPaths`, `IdentityForwardingFilter`
strips every client `X-User-*` and `X-Seller-Id` then injects `X-User-Id` + `X-User-Type=MEMBER`,
`WebSocketTokenFilter` turns `?access_token=` on `/ws/**` into the Bearer header), `machine`
(`X-Service-Token = v1.<service>.<epoch>.<hmacSha256(service:epoch)>` on routes whose metadata says
`serviceToken: true`), `gateway` (route filter `SellerHeader` copies `X-User-Id` into `X-Seller-Id` on the
announce-management routes only) and `web` (`SpaHistoryFallbackFilter`, `ProblemDetails`). Routes are the
table of §3 in `config/application.yml`, every target a property (`announce.routes.*`,
`announce.search.searcher-url|suggest-url`) so tests point them at MockWebServer and a local run needs no
Eureka. The `webapp` Maven profile (active by default) installs node v20.16.0 under `target/`, runs `npm ci`
and `npm run build`; webpack writes into `target/classes/static`.

**SPA** `@announce/customer-web` (React 18, TypeScript 5, react-router 6, Redux Toolkit, axios,
react-bootstrap, i18next en/da/sv, webpack 5, jest): shell with kind links, suggest box (`/api/suggest`)
and language switch; auth (login, register, profile; access token in memory only, silent single-flight
refresh on 401 through the cookie); home (category roots per kind from `/api/categories?kind=`, latest per
kind from search sorted by `created_epoch`); search (URL is the source of truth; facets rendered from the OCS
response: term facets as checkboxes, interval facets as min/max; `source` badge with the domain and
`/out/{id}` in a new tab for crawled listings; sort from `sortOptions`; pagination from `matchCount`);
announce detail per kind; post wizard (kind, category tree of that kind, kind form, location and picture
URLs, preview, save draft, publish) with `validation.ts` mirroring the aggregate rules; my announces with
edit, publish, withdraw and mark sold. Phase 2 behind `localStorage.announceFeatures`
(`{"trade":true,"chat":true,"mocks":true}`): auction panel, buy box and orders against the §8 contract,
contact-seller, inbox and thread (WebSocket with REST polling fallback) against §9, MSW handlers in
`app/mocks` (the WebSocket is not mocked; the thread polls).

**Tests.** `mvn -pl gateway-api -P '!webapp' test`: 16 tests — `EdgeRoutesTest` (13, WebTestClient
against one MockWebServer standing in for every backend, in-memory auth stores: public GET without token,
write without token 401, valid token -> `X-User-Id`/`X-User-Type`/`X-Seller-Id` downstream, spoofed headers
dropped, order/messaging routes need a member and get no seller header, webhooks public POST only, click-out
public, search and suggest SetPath, SPA fallback and 404 for a missing asset, WebSocket handshake with the
query token, login/refresh rotation/logout revocation, problem details), `MachineTokensTest`,
`AuthPersistenceTest` (2, live MySQL). `db-reactive`: `AnnounceRefreshTokenMapperTest` plus the 39-mapper
smoke test. SPA: 29 jest tests (post validation per kind, search state/URL/OCS query, auth slice, refresh
interceptor incl. single flight, i18n parity), `npm run i18n:check` green.

**Checklist of §7, executed 2026-09-26** with announce-management on 8085 (`mvn -pl announce-management
-P '!webapp' spring-boot:run`), the live MySQL, `docker/cqrs` (ESDB, Elasticsearch, OCS indexer/searcher/
suggest; Kafka came from the litemall compose already holding 9092) and the edge jar started as
`java -jar gateway-api/target/gateway-api-0.0.1-SNAPSHOT.jar --server.port=8090 --eureka.client.enabled=false
--spring.cloud.config.enabled=false --announce.routes.announce-management=http://localhost:8085` (port 8090
because a Jenkins holds 8080 on the development machine; 8080 stays the configured default):

| Step | Result |
|---|---|
| `GET /` and `GET /announce/abc` serve the SPA `index.html` from the jar | OK |
| Register through `/auth/register`, log in, `/auth/me` | 201 / 200 / 200 (member 5) |
| Post one announce of each kind through `/api/announces` | 201 DRAFT ×3 |
| Publish each | 200 PUBLISHED ×3 |
| Event with end before start | 409 `An event must start before it ends` |
| Job with sale type AUCTION | 409 `A job announce cannot be auctioned` |
| Write without token | 401 problem detail, backend not called |
| Find each in `/api/search?q=…&kind=` with the kind facet | GOODS, JOB, EVENT found, `source=local`, facet `kind` = the kind |
| Crawled listings | announce-management's import job loaded `dba_cars.json` at start (54 rows); `/api/search?source=dba` lists them with `external_url` |
| `GET /out/dba-1117479380` through the edge | 302 to `https://www.dba.dk/toyota-aygo-10-benzin/id-1117479380/`, `click_count` 1; unknown id 404 |
| My announces (`seller=<me>&status=PUBLISHED`), withdraw the event | total 3, WITHDRAWN |
| Refresh with the cookie, logout, refresh again | 200 / 204 / 401 |

**Deviations and environment fixes.**
- `docker/cqrs`: the suggest container is published on host port **8536** (was 8081, the gateway-admin port);
  `application.search-service.yml` moved `weightedFields` out of `settings` (the current
  `ocs-search-service:latest` binds `settings` to an enum map and refused to start); the ESDB volume needed
  `chown 100:101` (image user `esdb`) on this machine. A fresh Elasticsearch has no `announce_index`: the
  indexer's `update` API refuses to create it (`langcode required for new index`), so bootstrap it once with
  `GET :8535/indexer-api/v1/full/start/announce_index?locale=en` then `POST …/full/done` with the returned
  session, or let a full listings reindex do it.
- Machine token: HMAC header on routes flagged by metadata (not "every `lb://` route"), so the same rule
  applies when a test or a local run points a route at a plain URL. The OCS routes carry no token.
- The edge answers its own 401/403 as `application/problem+json`; auth validation errors carry an `errors`
  array. Bad credentials are 401, a taken username 409, a disabled account 403.
- `/auth/me` reads `X-User-Id` injected by the identity filter, like every downstream service.
- The dev RSA keypair is committed in `application.yml` (like litemall); production must set
  `ANNOUNCE_JWT_PRIVATE_KEY_PEM` / `ANNOUNCE_JWT_PUBLIC_KEY_PEM` and `ANNOUNCE_MACHINE_SECRET`.
- Not done (follow-ups): announce-management reading `X-User-Id` (then drop `SellerHeader`), backends
  verifying `X-Service-Token`, profile editing, image upload, a real WebSocket mock for the chat screens,
  `gateway-admin`.

### 9.1 Pictures and the reindex (2026-09-27)

After the first run the announces showed no pictures: the search index had been recreated empty and the
search projection only indexes on events, so the 240 seed announces (which carry CDN picture URLs) were
absent from home and search; the announces posted through the wizard had no pictures because it only
accepted pasted URLs. Two additions:

- announce-management: `AnnounceReindexService` + `POST /api/admin/announces/reindex` (class
  `AnnounceAdminController`, the place the gateway-admin block reserves for moderation) walk the PUBLISHED
  rows of the read model and push every document again. Covered in `AnnounceKindsEndToEndTest`.
- gateway-api: package `storage` — `POST /api/storage/upload` (member, multipart `file`, images only,
  10 MB) stores under `announce.storage.dir` and in `announce_storage`, `GET /api/storage/fetch/{key}` is
  public; `StorageRoutesTest`. The wizard's gallery step got an uploader (pick files, reorder, remove, the
  first is the main picture) next to the URL box; the detail page renders HTML descriptions through
  DOMPurify (the migrated litemall goods carry `<p>` and `<img>`).

The moderation endpoints of the gateway-admin block will land in the same `AnnounceAdminController`; that
worktree also adds a `moderated_by` column to `announce`, so whoever installs `db-reactive` into the local
Maven repository last decides which mapper the other worktree's tests see (reinstall your own before testing).

