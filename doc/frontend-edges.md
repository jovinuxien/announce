# The two frontend edges: `gateway-api` (customers) and `gateway-admin` (staff)

Status: design written 2026-09-26. `gateway-admin` implemented 2026-09-26 (section 4a records the
decisions taken while building it); `gateway-api` in its own worktree. Worktrees in `CLAUDE.md`. Model: litemall's `litemall-gateway-api` and
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
  `user_type=ADMIN` (`db-reactive/sql/announce_auth.sql`, id is a UUID chosen by the edge), 30 days,
  single use: `/auth/refresh` revokes and reissues, `/auth/logout` revokes, a reused token is 401 and the
  cookie is cleared. Cookie `announce_admin_refresh`: httpOnly, `Path=/auth`, `SameSite=Strict`,
  `Secure` under `prod`.
- **Route targets from properties.** Every route URI is `${announce.routes.<service>}` (default
  `lb://<service>`). The `dev` profile points announce-management at `http://localhost:8085` and
  messaging at `http://localhost:8086` because neither registers with Eureka yet; tests point them at
  MockWebServer. Eureka is off unless `ANNOUNCE_EUREKA_ENABLED=true`.
- **Machine token.** `X-Service-Token = gateway-admin.<epoch-seconds>.<hmac-sha256>` over
  `announce.machine.secret`; the staff JWT and the cookies are removed from every proxied request.
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
