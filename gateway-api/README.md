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
(`X-Service-Token`), `announce.search.searcher-url|suggest-url`, `announce.routes.*` (route targets).

## Packages

- `auth` — `AuthController` (`/auth/login|register|refresh|logout|me`), ports `UserAccounts` and
  `RefreshTokenStore` with R2DBC adapters over db-reactive, `RefreshTokenService` (rotating, hashed),
  `AccessTokens` (RS256), `RefreshCookies` (httpOnly, path `/auth`).
- `security` — `SecurityConfig` (deny by default), `PublicPaths`, `IdentityForwardingFilter` (strips client
  `X-User-*`/`X-Seller-Id`, injects the trusted ones), `WebSocketTokenFilter` (`access_token` query parameter).
- `machine` — `MachineTokenRelayFilter` adds `X-Service-Token` on routes with metadata `serviceToken: true`.
- `gateway` — route filter `SellerHeader` (copies `X-User-Id` into `X-Seller-Id` for announce-management).
- `web` — `SpaHistoryFallbackFilter` (deep links -> `index.html`), `ProblemDetails` (401/403 bodies).

## SPA feature flags

Phase 2 screens are off by default. In the browser console:

```
localStorage.announceFeatures = '{"trade":true,"chat":true,"mocks":true}'
```

`mocks` starts MSW (`app/mocks`) against the `doc/order-context.md` §8/§9 contracts, so the auction panel,
buy flow, orders and chat work without the order and messaging services.
