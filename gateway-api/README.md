# gateway-api — the customer edge

Spring Cloud Gateway (WebFlux) on port 8080 in front of `announce-management`, `order`, `messaging` and the
OCS search tier, the member identity (`/auth/*`, RS256 JWT over `announce_user`), and the React SPA
`@announce/customer-web` served from the same jar. Design: `doc/frontend-edges.md`; guide: `doc/gateway-api-guide.pdf`.

## Build

| Goal | Command |
|---|---|
| Edge only, tests (MockWebServer; the two persistence tests use the live MySQL) | `mvn -pl gateway-api -P '!webapp' test` |
| Edge + SPA jar (installs node v20.16.0 under `target/`, `npm ci`, `npm run build`) | `mvn -pl gateway-api -P webapp -DskipTests package` |
| SPA alone | `cd src/main/webapp && npm ci && npm test && npm run i18n:check && npm run build` |
| SPA dev server on 9000, proxying `/api /auth /out /ws` to the edge | `npm run webapp:dev` (`EDGE_URL=http://localhost:8090` when 8080 is taken) |

## Run

```
java -jar target/gateway-api-0.0.1-SNAPSHOT.jar
# without Eureka and the config server (targets by URL):
java -jar target/gateway-api-0.0.1-SNAPSHOT.jar --server.port=8090 --eureka.client.enabled=false \
  --spring.cloud.config.enabled=false --announce.routes.announce-management=http://localhost:8085
```

Properties: `announce.jwt.*` (dev keypair in `application.yml`, override with `ANNOUNCE_JWT_PRIVATE_KEY_PEM` /
`ANNOUNCE_JWT_PUBLIC_KEY_PEM`), `announce.auth.*` (refresh cookie), `announce.machine.secret`
(`X-Service-Token`, env `ANNOUNCE_SERVICE_SECRET`, the same value as the backends' `announce.service-token.secret`), `announce.search.searcher-url|suggest-url`, `announce.routes.*` (route targets).

## Packages

- `auth` — `AuthController` (`/auth/login|register|refresh|logout|me`), ports `UserAccounts` and
  `RefreshTokenStore` with R2DBC adapters over db-reactive, `RefreshTokenService` (rotating, hashed),
  `AccessTokens` (RS256), `RefreshCookies` (httpOnly, path `/auth`).
- `security` — `SecurityConfig` (deny by default), `PublicPaths`, `IdentityForwardingFilter` (strips client
  `X-User-*`, `X-Staff-Id`, `X-Admin-Id`, `X-Seller-Id` and `X-Service-Token`, injects the trusted `X-User-Id` and
  `X-User-Type=MEMBER`), `WebSocketTokenFilter` (`access_token` query parameter).
- `machine` — `MachineTokenRelayFilter` adds `X-Service-Token` on routes with metadata `serviceToken: true`; the
  backends verify it (`announce.service-token.mode=enforce`) and answer 401 to anything that bypassed an edge.
- `web` — `SpaHistoryFallbackFilter` (deep links -> `index.html`), `ProblemDetails` (401/403 bodies).
- `storage` — pictures of the announces: `POST /api/storage/upload` (member, multipart field `file`, images only,
  `announce.storage.max-size` 10 MB) stores the file under `announce.storage.dir` (default `~/announce_storage`),
  records it in `announce_storage` and answers `{key, url}`; `GET /api/storage/fetch/{key}` serves it to everyone
  (30-day cache). `announce.storage.public-base` prefixes the stored URL (empty = relative, same origin).

## Pictures and the search index

The post wizard uploads pictures through the edge and puts the returned URLs into the announce gallery; the
first one is the main picture (`pictureUrl` in listings, `image_url` in the search index). Seed announces keep
their original CDN URLs. Descriptions that contain HTML (the migrated goods) are rendered sanitised.

The search projection indexes announces on their events only. Rows that never produced an event (the seed data)
or a rebuilt Elasticsearch need one full pass from the read model, through announce-management's admin trigger
(reached through the admin edge; a direct call is answered 401 unless the service was started with
`--announce.service-token.mode=off`, and then the staff identity goes in the headers):

```
curl -X POST http://localhost:8085/api/admin/announces/reindex -H 'X-User-Id: 1' -H 'X-User-Type: ADMIN'   # {"indexed": 240}
curl -X POST http://localhost:8085/api/admin/listings/reindex                                              # crawled listings, same idea
```

## SPA feature flags

Phase 2 screens are off by default. In the browser console:

```
localStorage.announceFeatures = '{"trade":true,"chat":true,"mocks":true}'
```

`mocks` starts MSW (`app/mocks`) against the `doc/order-context.md` §8/§9 contracts, so the auction panel,
buy flow, orders and chat work without the order and messaging services.
