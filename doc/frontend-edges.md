# The two frontend edges: `gateway-api` (customers) and `gateway-admin` (staff)

Status: design written 2026-09-26; both edges implemented 2026-09-26: `gateway-api` (section 9 records what was
built and the checklist run) and `gateway-admin` (section 4a records its decisions, section 7a its checklist).
Worktrees `gateway-api` and `gateway-admin` in `CLAUDE.md`. Model: litemall's `litemall-gateway-api` and
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
  edge, `X-User-Roles`. The customer edge also strips `X-Staff-Id`, `X-Admin-Id`,
  `X-Seller-Id` and `X-Service-Token`. announce-management reads the owner of an announce
  from `X-User-Id` with `X-User-Type=MEMBER`; there is no `X-Seller-Id` any more (§10).
- **Machine token.** `MachineTokenRelayFilter` adds `X-Service-Token` (HMAC over the shared
  secret `ANNOUNCE_SERVICE_SECRET`) on the routes to our own services. The backends verify it
  and answer 401 to traffic that did not pass an edge (§10).
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

### 4a. Admin edge as built (2026-09-26)

- **Tokens.** RS256 through Nimbus (`spring-security-oauth2-jose`, no extra library). Claims `iss`/`aud`
  `announce-admin`, `sub` = admin id, `typ` `ADMIN`, `uid`, `username`, `avatar`, `roles`, `roleIds`, 2 h.
  Blank `announce.jwt.*-key-pem` selects the committed development pair `config/dev-admin-jwt-*.pem`
  with a warning; the `prod` profile refuses to start without real keys (`JwtKeyGuard`).
- **Roles to authorities.** `ROLE_ADMIN` for any account with at least one enabled, non-deleted role;
  `ROLE_SUPER` when one of those roles holds the permission `*` (seed: role 1, `admin123`). An account
  without an enabled role cannot sign in. Other permission strings are stored and editable
  (`PermissionCatalogue`) but not enforced yet.
- **Refresh tokens.** Opaque 256-bit value, SHA-256 stored in `announce_refresh_token` with
  `user_type=ADMIN` (table defined in `db-reactive/sql/announce_table.sql`, shared with `gateway-api`; id is a UUID chosen by the edge), 30 days,
  single use: `/auth/refresh` revokes and reissues, `/auth/logout` revokes, a reused token is 401 and the
  cookie is cleared. Cookie `announce_admin_refresh`: httpOnly, `Path=/auth`, `SameSite=Strict`,
  `Secure` under `prod`.
- **Route targets from properties.** Every route URI is `${announce.routes.<service>}` (default
  `lb://<service>`). The `dev` profile points announce-management at `http://localhost:8085` and
  messaging at `http://localhost:8086` because neither registers with Eureka yet; tests point them at
  MockWebServer. Eureka is off unless `ANNOUNCE_EUREKA_ENABLED=true`.
- **Machine token.** `X-Service-Token = v1.gateway-admin.<epochSeconds>.<hmacSha256(issuer:epochSeconds)>` over
  `announce.machine.secret` (the same layout and secret as the customer edge since 2026-10-01, §10); the staff
  JWT and the cookies are removed from every proxied request.
- **Edge controllers.** `/api/admin/users`, `/api/admin/admins`, `/api/admin/roles` (+ `/all`, `/{id}/permissions`),
  `/api/admin/permissions`, `/api/admin/regions`, `/api/admin/dashboard`, `/api/admin/listing-stats`
  (counts per source and status, recent GONE rows, most clicked; import and reindex stay in
  announce-management). REST-shaped, `{items, page, size, total}` pages, RFC 7807 problems with a `code`
  on conflicts (`username-taken`, `self-delete`, `role-in-use`, `role-name-taken`, `region-has-children`).
- **Moderation in announce-management.** New commands `ModerateWithdraw` and `ModerateExpire`
  (no owner check, mandatory reason, only on PUBLISHED); `AnnounceWithdrawn` and `AnnounceExpired`
  carry `moderatedBy` and `reason` (null for seller and scheduler actions); the read model gained
  `announce.moderated_by` / `moderation_reason`, cleared by the next transition. Exposed through
  `AnnounceAdminController` at `/api/admin/announces` (list in any status, `GET {id}`,
  `POST {id}/withdraw|expire {reason}` with the moderator from `X-User-Id`).
- **SPA dev server** on 9001 (the customer SPA keeps 9000), proxying `/api` and `/auth` to 8081.
- **Staff chat (2026-10-10, §11).** Routes `/api/conversations/**` and `/ws/**` to messaging, the token on a
  handshake as `?access_token=` (`WebSocketTokenFilter`), `ROLE_ADMIN` on `/ws/**`; `X-Staff-Id` and
  `X-Admin-Id` are stripped from clients, and every backend reads staff from `X-User-Id` + `X-User-Type=ADMIN`.

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

### 7a. Admin edge checklist, executed 2026-09-26

Setup: live MySQL `announce_reactive_db`, `docker/cqrs` (EventSourcingDB, OCS indexer), announce-management
started from its jar on 8085, `gateway-admin` from its jar with `--spring.profiles.active=dev` on 8081
(routes to `http://localhost:8085`). Automated first: `mvn -pl db-reactive,gateway-admin -P '!webapp' test`
(19 tests: routes, auth flow, edge CRUD), `mvn -pl db-reactive,announce-management -P '!webapp' test`
(50 tests incl. the moderation end-to-end case), `npm test` (28), `npm run i18n:check` (421 keys, en/da/sv),
`mvn -pl db-reactive,gateway-admin -P webapp -DskipTests package` (jar contains `static/index.html` and
`static/assets/js/main.*.js`).

| Step | Result |
|---|---|
| `POST /auth/login` admin123 | 200, access token with `roles [ROLE_ADMIN, ROLE_SUPER]`, httpOnly cookie `announce_admin_refresh; Path=/auth` |
| `GET /auth/me`, `GET /api/admin/dashboard?days=7` | 200; 239 published goods, 54 active DBA listings, 2 members, 3 admins, 101 categories, 49 brands |
| Create a category of each kind (`POST /api/categories` GOODS, JOB, EVENT) | 201 x3; the EVENT root appears in `GET /api/categories?kind=EVENT` |
| Withdraw an announce as staff | post + publish a goods announce as seller 1, then `POST /api/admin/announces/{id}/withdraw {"reason":...}` through the edge: 200 WITHDRAWN; the admin listing shows `moderatedBy 1` and the reason within 4 s |
| Withdraw a seed row (`1006002`) | 404 `urn:announce:cqrs:subject-not-found`: the migrated seed rows exist only in the read model, they have no event stream. Expected; only announces posted through the API can be moderated |
| Trigger a listings import (`POST /api/admin/listings/import` with `done/dba_cars.json`) | 200 `{"seen":54,"inserted":0,"updated":0,"unchanged":54,"rejected":0}`; `POST .../reindex` 200; `GET /api/admin/listing-stats` lists the top clicked listing with its source url |
| Create a role with permissions and an admin holding it | role 201, `PUT /{id}/permissions` 200, admin 201 (password never returned); the new admin logs in with `roles [ROLE_ADMIN]`, gets 403 on `/api/admin/admins` and 200 on the dashboard; both deleted afterwards (204, 204) |
| SPA | `GET /` and `GET /announces/abc` answer `index.html` (200, text/html); `/assets/js/main.*.js` 200; `GET /api/admin/announces` without a token 401 problem |

Observations: announce-management's `/actuator/health` reports DOWN while the API works (a health
indicator of an unused binder; not caused by this change). The three "Checklist <KIND> 2026-09-26"
root categories were left in the database as visible evidence; hide them from the categories screen.

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
- Follow-ups done since: announce-management reads `X-User-Id` and the `SellerHeader` filter is gone, the
  backends verify `X-Service-Token` (both in §10), image upload (§9.1), `gateway-admin` (§4a). Still not done:
  profile editing, a real WebSocket mock for the chat screens. Where this section mentions `X-Seller-Id`,
  `SellerHeader` or `ANNOUNCE_MACHINE_SECRET`, §10 supersedes it.

### 9.1 Pictures and the reindex (2026-09-27)

After the first run the announces showed no pictures: the search index had been recreated empty and the
search projection only indexes on events, so the 240 seed announces (which carry CDN picture URLs) were
absent from home and search; the announces posted through the wizard had no pictures because it only
accepted pasted URLs. Two additions:

- announce-management: `AnnounceReindexService` + `POST /api/admin/announces/reindex` (on gateway-admin's
  `interfaces.rest.admin.AnnounceAdminController`, next to the moderation endpoints) walk the PUBLISHED
  rows of the read model and push every document again. Covered in `AnnounceKindsEndToEndTest`.
- gateway-api: package `storage` — `POST /api/storage/upload` (member, multipart `file`, images only,
  10 MB) stores under `announce.storage.dir` and in `announce_storage`, `GET /api/storage/fetch/{key}` is
  public; `StorageRoutesTest`. The wizard's gallery step got an uploader (pick files, reorder, remove, the
  first is the main picture) next to the URL box; the detail page renders HTML descriptions through
  DOMPurify (the migrated litemall goods carry `<p>` and `<img>`).

Build note: the worktrees share one local Maven repository, so whoever installs `db-reactive` last decides
which mappers the other worktrees' tests see; after a merge that removes mappers, `mvn -pl db-reactive clean
install` is needed, because a removed mapper XML survives in `target/classes` and gets packaged otherwise.

## 10. Edge trust: verified service token and one identity header (2026-10-01)

Until this step the backends trusted `X-User-Id` and friends from anyone who could reach their port, and the
customer edge copied `X-User-Id` into a second header for announce-management. Both are closed.

**The token.** Both edges sign every request they proxy to one of our services:

```
X-Service-Token = v1.<service>.<epochSeconds>.<hex hmacSha256(secret, service + ":" + epochSeconds)>
```

`<service>` is `gateway-api` or `gateway-admin` (or the name of a backend calling another backend). The secret
is one value for the whole system, `ANNOUNCE_SERVICE_SECRET` (dev default `dev-service-secret-change-me`;
`ANNOUNCE_MACHINE_SECRET` is gone). The admin edge used a three-part layout before; it now mints the same
layout as the customer edge. The edges do not depend on `cqrs-reactive`, so each keeps its own small minting
class; one test vector (secret `test-secret`, service `gateway-api`, epoch `1767225600`) is asserted in
`ServiceTokensTest` (cqrs-reactive) and in both edges' `MachineTokensTest`. The token is not bound to the
request: it only ever travels between our own processes and is refused when older than the allowed skew.

**The check.** `cqrs-reactive` has a `security` package (auto-configured, `doc/cqrs-reactive.md` §5a). In each
backend:

```yaml
announce:
  service-token:
    mode: enforce          # off | log | enforce
    secret: ${ANNOUNCE_SERVICE_SECRET:dev-service-secret-change-me}
```

| Mode | Behaviour |
|---|---|
| `off` | no check (the `test` profiles; a service run on its own) |
| `log` | the token is checked, a failure is logged, the request is served (for rolling the check out) |
| `enforce` | a missing, stale (more than 60 s off, `max-skew`) or forged token is answered 401 `application/problem+json` (`urn:announce:cqrs:service-token-required` / `-invalid`) |

`/actuator/**` stays open (`open-paths`; announce-management adds the API docs paths). The check covers the
WebSocket handshake of messaging. announce-management, order and messaging ship with `enforce`; under the
`prod` profile announce-management refuses to start without `ANNOUNCE_SERVICE_SECRET`.

**Service to service.** messaging looks an announce up in announce-management; its client opts in with
`cqrs.api.clients.announce-management.service-token: true` and then signs its calls as `messaging`. The
opt-in is per client so the token never goes to the OCS tier.

**Headers a client can no longer send.** The customer edge strips `X-User-*`, `X-Staff-Id`, `X-Admin-Id`,
`X-Seller-Id` and `X-Service-Token` from every request before it injects `X-User-Id` and
`X-User-Type=MEMBER`. Before, a signed-in member could add `X-Staff-Id` and messaging would treat the request
(and the WebSocket) as staff.

**One identity header in announce-management.** `interfaces.rest.Callers` reads the caller:

| Controller | Rule |
|---|---|
| `/api/announces` writes | `X-User-Id` with `X-User-Type=MEMBER`: 401 `no-principal` without an id, 403 `not-a-member` for another type |
| `/api/announces` reads | the same pair identifies the viewer (owners see their drafts); anyone else is a visitor |
| `/api/admin/announces/**` (all routes) | `X-User-Id` with `X-User-Type=ADMIN`: 401 without an id, 403 `not-staff` for a member |

`X-Seller-Id` is ignored and the edge's `SellerHeader` route filter is deleted. Member ids and staff ids are
different number spaces, which is why the type is checked on both sides.

**Working locally.** A direct `curl` to 8085 / 8083 / 8086 now answers 401. Go through an edge, or start the
service with `--announce.service-token.mode=off` and send the identity headers yourself:

```
curl -X POST http://localhost:8085/api/admin/announces/reindex -H 'X-User-Id: 1' -H 'X-User-Type: ADMIN'
```

**Deploying.** The edges and the backends must hold the same secret. announce-management must be deployed
before or together with the customer edge (an old announce-management still needs `X-Seller-Id`). To roll the
check out without a window of 401s, start the backends with `mode=log`, deploy the edges, then switch to
`enforce`.

**Tests.** `ServiceTokensTest`, `ServiceTokenWebFilterTest`, `HttpExchangeClientsTest` (cqrs-reactive);
`EdgeRequests` in the test kit signs a suite's requests, and the three end-to-end suites
(`AnnounceKindsEndToEndTest`, `OrderModuleEndToEndTest`, `MessagingEndToEndTest`) run with `enforce` and
assert the 401s, including an unsigned WebSocket handshake; `EdgeRoutesTest` of the customer edge asserts that
spoofed staff headers and a client's own service token never reach a backend.

**Manual check, executed 2026-10-01** with announce-management on 8085 (`enforce`, dev secret, import and
scheduled jobs off) and the customer edge on 8090, both from this branch's classes, on the live MySQL and
`docker/cqrs`:

| Step | Result |
|---|---|
| Direct `GET :8085/api/announces`, no token | 401 `service-token-required` |
| Direct `GET :8085/api/admin/announces` with `X-User-Id: 1`, `X-User-Type: ADMIN` | 401 |
| Direct call with a well-formed token that is old and signed with another secret | 401 |
| Direct `POST :8085/api/announces` with member headers | 401 |
| Direct `GET :8085/actuator/health` | answered (503: the legacy Redis probe is down, as before), not 401 |
| `GET :8090/api/announces` through the edge, anonymous | 200 |
| `POST :8090/api/announces` through the edge, not signed in | 401 |
| Register and log in through the edge | 201 / 200 (member 26, `edgetrust_1001`) |
| Post a goods draft through the edge while sending `X-User-Id: 1`, `X-Seller-Id: 1`, `X-User-Type: ADMIN` | 201 DRAFT, `sellerId` 26 |
| Owner reads the draft and lists `seller=26&status=DRAFT` through the edge | 200 DRAFT; total 1 |
| Visitor reads the draft, asks for the same listing | 404; total 0 |
| `/api/admin/announces` through the customer edge as that member | 404 (not routed) |

The check left member 26 and one DRAFT announce (`42dabea8-691b-469c-bafa-77354fc2f24d`, titled "Edge trust
check (draft, safe to delete)") in the development database; a draft cannot be withdrawn.

**Not part of this step.** The admin edge forwarded staff as `X-User-Id` + `X-User-Type=ADMIN`, while the
admin routes of order read `X-Admin-Id` and those of messaging read `X-Staff-Id`; closed in §11.
`/api/admin/listings/**` in announce-management has no caller check of its own.

Build note: `announce-management/src/main/resources/config/application-prod.yml` was an empty file and is not
any more. A `target/` built before this change holds an empty copy, and `maven-resources-plugin` 3.3.0 then
fails with `copy-resources failed: newPosition < 0`. Delete
`announce-management/target/classes/config/application-prod.yml` (or run `mvn -pl announce-management clean`) once.

## 11. Staff identity: one caller convention for every backend (2026-10-10)

After §10 the admin edge forwarded staff as `X-User-Id` + `X-User-Type=ADMIN` + `X-User-Roles`, but the staff
routes of order still read `X-Admin-Id` and those of messaging `X-Staff-Id`, so nothing staff did on those two
services worked through the admin edge; and the admin edge had no route for the staff chat at all. Both are
closed, "according to our backend": the backends keep the convention announce-management already had, and the
admin edge is completed around it. Nothing here involves an outside party: two edges, three backends, one
header pair.

**The convention.** `cqrs-reactive` (`security` package) has a `Caller` record and a `Callers` helper
(`doc/cqrs-reactive.md` §5a). Every service reads its callers through it:

| Call | Who passes | Otherwise |
|---|---|---|
| `Callers.member(headers)` | `X-User-Type=MEMBER` | 401 `no-principal` without an id, 403 `not-a-member` for staff |
| `Callers.staff(headers)` | `X-User-Type=ADMIN` | 401 `no-principal`, 403 `not-staff` for a member |
| `Callers.any(headers)` | either | 401 `no-principal` |
| `Callers.memberIfAny(headers)` | a member, else empty | never fails for a visitor or staff |

An id with a missing or unknown type is 403 `unknown-caller-type`; a non-numeric id is 400. `X-Seller-Id`,
`X-Staff-Id` and `X-Admin-Id` are read nowhere.

**Per module.**

| Module | Change |
|---|---|
| order | The six staff routes (`/api/admin/bids/{id}/cancel`, `/api/admin/auctions/{announceId}/cancel`, `/api/admin/auctions/close-due`, `/api/admin/orders/{id}` GET, `/resolve-dispute`, `/release`) read `Callers.staff`; the seventeen member parameters read `Callers.member`, so a staff id is no longer taken for the member with the same number. `X-Admin-Id` gone. |
| messaging | `Principals` is a thin layer over `Callers` (viewer, member, staff); `AdminMessagingController`, `/ws/staff` and `/ws/conversations/{id}` read the same pair. `X-Staff-Id` gone. |
| announce-management | its `Callers` delegates to the framework; behaviour unchanged. |
| gateway-admin | routes `/api/conversations/**` (staff read, post in and close the support conversations they joined) and `/ws/**` (`/ws/staff` bulletins, `/ws/conversations/{id}`) to messaging; `WebSocketTokenFilter` turns `?access_token=` into the bearer header, as on the customer edge; `ROLE_ADMIN` on `/ws/**`; `X-Staff-Id` / `X-Admin-Id` stripped from clients; the `dev` profile points order at 8083 and the WebSocket route at `ws://localhost:8086`. |

**Tests.** `CallersTest` (framework); `OrderModuleEndToEndTest` sends members and staff as the edges do and
asserts the two 403s and that `X-Admin-Id` is not an identity; `MessagingEndToEndTest` and
`ConversationControllerTest` the same for messaging, including the staff WebSocket handshake;
`EdgeRoutesTest` of the admin edge proves the staff identity reaches order and messaging, that the former staff
headers are dropped, and that the `/ws/staff` handshake with the query token is forwarded with the identity and
without the JWT. The load driver `ChatLoadDriver` sends the pair too.

**Manual run.** Not executed: the plan ended with a run through the admin edge against a local messaging
service, but another session was running messaging on port 8086 against the shared EventSourcingDB, Kafka and
MySQL, and a second node would have joined its consumer groups. While that instance ran, `MessagingEndToEndTest`
(whose read model is the live database) stalled because the instance kept overwriting the checkpoint rows the
suite had reset; it passed as soon as the instance stopped. Run the suite when no messaging instance uses
`announce_reactive_db`.

**Still open.** The admin SPA's phase-2 screens run on mocks that call list endpoints the order backend does not
have (`GET /api/admin/auctions`, `GET /api/admin/orders`, `GET /api/admin/payouts`); wiring those screens to the
real backends is separate work. Roles are forwarded but not checked inside the backends (the edge enforces
`ROLE_ADMIN`, and `ROLE_SUPER` on its own admin, role and permission routes).
