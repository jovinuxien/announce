package org.linlinjava.announce.gatewayapi;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.gatewayapi.auth.AccessTokens;
import org.linlinjava.announce.gatewayapi.auth.Account;
import org.linlinjava.announce.gatewayapi.auth.InMemoryAuthStores;
import org.linlinjava.announce.gatewayapi.machine.MachineTokenProperties;
import org.linlinjava.announce.gatewayapi.machine.MachineTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The customer edge against one MockWebServer standing in for every backend: public reads pass, writes need a
 * member, trusted identity headers replace spoofed ones, the SPA shell answers deep links, the search route is
 * rewritten to the OCS tenant API, the WebSocket handshake carries the identity, and refresh tokens rotate and
 * revoke. No MySQL: {@link InMemoryAuthStores} replaces the two persistence ports.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(InMemoryAuthStores.class)
class EdgeRoutesTest {

    static MockWebServer backend;

    @LocalServerPort int port;
    @Autowired AccessTokens accessTokens;
    @Autowired MachineTokenProperties machine;
    WebTestClient client;

    @BeforeAll
    static void startBackend() throws Exception {
        backend = new MockWebServer();
        backend.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath().startsWith("/ws/")) {
                    return new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                        @Override
                        public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                            webSocket.send("hello-from-messaging");
                        }
                    });
                }
                return new MockResponse().setResponseCode(200)
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .setBody("{\"path\":\"" + request.getPath() + "\"}");
            }
        });
        backend.start();
    }

    @AfterAll
    static void stopBackend() {
        try {
            backend.shutdown();
        } catch (java.io.IOException e) {
            // the gateway keeps the proxied WebSocket connection pooled; MockWebServer gives up after 5 s, which is fine
        }
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry r) {
        String http = "http://localhost:" + backend.getPort();
        r.add("announce.routes.announce-management", () -> http);
        r.add("announce.routes.order", () -> http);
        r.add("announce.routes.messaging", () -> http);
        r.add("announce.routes.messaging-ws", () -> "ws://localhost:" + backend.getPort());
        r.add("announce.search.searcher-url", () -> http);
        r.add("announce.search.suggest-url", () -> http);
    }

    @BeforeEach
    void setUp() throws Exception {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).responseTimeout(Duration.ofSeconds(10)).build();
        while (backend.getRequestCount() > 0 && backend.takeRequest(10, TimeUnit.MILLISECONDS) != null) {
            // drain what an earlier test left behind
        }
    }

    private String memberToken() {
        return accessTokens.issue(new Account(InMemoryAuthStores.USER_ID, InMemoryAuthStores.USERNAME, "", "Alice", "", "", true)).token();
    }

    private RecordedRequest downstream() throws InterruptedException {
        RecordedRequest r = backend.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(r, "the backend should have received the request");
        return r;
    }

    @Test
    void publicCatalogReadPassesWithoutTokenAndWithoutIdentityHeaders() throws Exception {
        client.get().uri("/api/announces?kind=GOODS").header("X-User-Id", "999").header("X-Seller-Id", "999")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.path").isEqualTo("/api/announces?kind=GOODS");
        RecordedRequest r = downstream();
        assertNull(r.getHeader("X-User-Id"), "a spoofed X-User-Id must be dropped");
        assertNull(r.getHeader("X-Seller-Id"), "a spoofed X-Seller-Id must be dropped");
        assertNull(r.getHeader("X-User-Type"));
        assertNotNull(r.getHeader(MachineTokens.HEADER), "our own services get the machine token");
    }

    @Test
    void writeWithoutTokenIs401ProblemDetailAndNeverReachesTheBackend() {
        int before = backend.getRequestCount();
        client.post().uri("/api/announces").contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange().expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.status").isEqualTo(401);
        assertEquals(before, backend.getRequestCount());
    }

    @Test
    void validTokenYieldsTrustedIdentityAndAVerifiableServiceTokenDownstream() throws Exception {
        client.post().uri("/api/announces").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken())
                .header("X-User-Id", "999").header("X-User-Type", "ADMIN").header("X-Seller-Id", "999")
                .header(MachineTokens.HEADER, "v1.gateway-api.1.forged")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"title\":\"x\"}")
                .exchange().expectStatus().isOk();
        RecordedRequest r = downstream();
        assertEquals("1", r.getHeader("X-User-Id"));
        assertEquals("MEMBER", r.getHeader("X-User-Type"));
        assertNull(r.getHeader("X-Seller-Id"), "announce-management reads the owner from X-User-Id");
        assertEquals(1, r.getHeaders().values("X-User-Id").size(), "exactly one trusted X-User-Id");
        assertEquals(1, r.getHeaders().values(MachineTokens.HEADER).size(), "the client's own service token is replaced");
        assertTrue(MachineTokens.verify(machine, r.getHeader(MachineTokens.HEADER), Instant.now().getEpochSecond(), 60));
        assertEquals("{\"title\":\"x\"}", r.getBody().readUtf8());
    }

    @Test
    void orderAndMessagingRoutesNeedAMember() throws Exception {
        client.get().uri("/api/orders?role=buyer").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/conversations").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/orders?role=buyer").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken())
                .exchange().expectStatus().isOk();
        RecordedRequest r = downstream();
        assertEquals("1", r.getHeader("X-User-Id"));
    }

    @Test
    void aMemberCannotPoseAsStaffThroughTheCustomerEdge() throws Exception {
        client.get().uri("/api/conversations").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken())
                .header("X-Staff-Id", "1").header("x-admin-id", "1").header("X-User-Roles", "SUPER")
                .exchange().expectStatus().isOk();
        RecordedRequest r = downstream();
        assertEquals("1", r.getHeader("X-User-Id"));
        assertEquals("MEMBER", r.getHeader("X-User-Type"));
        assertNull(r.getHeader("X-Staff-Id"), "messaging treats X-Staff-Id as staff: a client must not be able to send it");
        assertNull(r.getHeader("X-Admin-Id"), "order treats X-Admin-Id as staff: a client must not be able to send it");
        assertNull(r.getHeader("X-User-Roles"));
    }

    @Test
    void aClientSuppliedServiceTokenNeverReachesARouteWithoutOne() throws Exception {
        client.get().uri("/api/search?q=x").header(MachineTokens.HEADER, "v1.gateway-api.1.forged")
                .exchange().expectStatus().isOk();
        assertNull(downstream().getHeader(MachineTokens.HEADER));
    }

    @Test
    void providerWebhooksArePublicPostsOnly() throws Exception {
        client.post().uri("/api/payments/webhook/fake").contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange().expectStatus().isOk();
        assertEquals("/api/payments/webhook/fake", downstream().getPath());
        client.get().uri("/api/payments/webhook/fake").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void clickOutIsPublic() throws Exception {
        client.get().uri("/out/dba-123").exchange().expectStatus().isOk();
        assertEquals("/out/dba-123", downstream().getPath());
    }

    @Test
    void searchAndSuggestAreRewrittenToTheOcsTenantApi() throws Exception {
        client.get().uri("/api/search?q=toyota&limit=12").exchange().expectStatus().isOk();
        RecordedRequest search = downstream();
        assertEquals("/search-api/v1/search/announce_index?q=toyota&limit=12", search.getPath());
        assertNull(search.getHeader(MachineTokens.HEADER), "the OCS tier does not get the machine token");

        client.get().uri("/api/suggest?userQuery=toy").exchange().expectStatus().isOk();
        assertEquals("/suggest-api/v1/announce_index/suggest?userQuery=toy", downstream().getPath());
    }

    @Test
    void spaFallbackServesIndexForDeepLinksAnd404ForMissingAssets() {
        client.get().uri("/announce/abc-123").accept(MediaType.TEXT_HTML).exchange().expectStatus().isOk()
                .expectBody(String.class).value(body -> assertTrue(body.contains("SPA-SHELL")));
        client.get().uri("/").exchange().expectStatus().isOk()
                .expectBody(String.class).value(body -> assertTrue(body.contains("SPA-SHELL")));
        client.get().uri("/post/goods").accept(MediaType.TEXT_HTML).exchange().expectStatus().isOk()
                .expectBody(String.class).value(body -> assertTrue(body.contains("SPA-SHELL")));
        client.get().uri("/app/main.js").exchange().expectStatus().isNotFound();
        client.get().uri("/api/unknown/thing").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void webSocketHandshakeCarriesTheIdentityFromTheQueryToken() throws Exception {
        ReactorNettyWebSocketClient ws = new ReactorNettyWebSocketClient();
        URI uri = URI.create("ws://localhost:" + port + "/ws/conversations/conv-1?access_token=" + memberToken());
        String first = ws.execute(uri, session -> session.receive().map(m -> m.getPayloadAsText()).next()
                        .flatMap(msg -> session.close().thenReturn(msg))
                        .then())
                .then(Mono.just("connected"))
                .block(Duration.ofSeconds(10));
        assertEquals("connected", first);
        RecordedRequest r = downstream();
        assertEquals("/ws/conversations/conv-1", r.getPath(), "the access_token parameter is stripped");
        assertEquals("websocket", r.getHeader("Upgrade").toLowerCase());
        assertEquals("1", r.getHeader("X-User-Id"));
        assertEquals("MEMBER", r.getHeader("X-User-Type"));
        assertTrue(MachineTokens.verify(machine, r.getHeader(MachineTokens.HEADER), Instant.now().getEpochSecond(), 60),
                "messaging refuses a handshake whose service token does not verify");
    }

    @Test
    void webSocketWithoutTokenIs401() {
        client.get().uri("/ws/conversations/conv-1").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void loginRefreshRotatesAndLogoutRevokes() {
        var login = client.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"alice\",\"password\":\"secret123\"}")
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.accessToken").exists().jsonPath("$.user.username").isEqualTo("alice")
                .returnResult();
        ResponseCookie first = login.getResponseCookies().getFirst("announce_refresh");
        assertNotNull(first);
        assertTrue(first.isHttpOnly());
        assertEquals("/auth", first.getPath());

        // the access token from the body opens /auth/me
        String access = com.jayway.jsonpath.JsonPath.read(new String(login.getResponseBody()), "$.accessToken");
        client.get().uri("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.id").isEqualTo(1);
        client.get().uri("/auth/me").exchange().expectStatus().isUnauthorized();

        // refresh rotates: a new cookie, and the old one is dead
        var refreshed = client.post().uri("/auth/refresh").cookie("announce_refresh", first.getValue())
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.accessToken").exists().returnResult();
        ResponseCookie second = refreshed.getResponseCookies().getFirst("announce_refresh");
        assertNotNull(second);
        assertNotEquals(first.getValue(), second.getValue());
        client.post().uri("/auth/refresh").cookie("announce_refresh", first.getValue())
                .exchange().expectStatus().isUnauthorized().expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);

        // logout revokes the current one and clears the cookie
        var logout = client.post().uri("/auth/logout").cookie("announce_refresh", second.getValue())
                .exchange().expectStatus().isNoContent().expectBody().returnResult();
        ResponseCookie cleared = logout.getResponseCookies().getFirst("announce_refresh");
        assertNotNull(cleared);
        assertEquals(0, cleared.getMaxAge().getSeconds());
        client.post().uri("/auth/refresh").cookie("announce_refresh", second.getValue())
                .exchange().expectStatus().isUnauthorized();
        client.post().uri("/auth/refresh").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void memberEditsProfileAndChangesPassword() {
        var login = client.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"alice\",\"password\":\"secret123\"}")
                .exchange().expectStatus().isOk().expectBody().returnResult();
        String access = com.jayway.jsonpath.JsonPath.read(new String(login.getResponseBody()), "$.accessToken");
        ResponseCookie firstDevice = login.getResponseCookies().getFirst("announce_refresh");
        assertNotNull(firstDevice);
        // a second device
        var other = client.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"alice\",\"password\":\"secret123\"}")
                .exchange().expectStatus().isOk().expectBody().returnResult();
        ResponseCookie otherDevice = other.getResponseCookies().getFirst("announce_refresh");
        assertNotNull(otherDevice);

        // profile: nickname, mobile, avatar, gender, birthday; the answer and GET /auth/me carry them
        client.put().uri("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"nickname\":\"Alice B.\",\"mobile\":\"+4512345678\",\"avatar\":\"/api/storage/fetch/k1\",\"gender\":2,\"birthday\":\"1990-05-01\"}")
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.nickname").isEqualTo("Alice B.").jsonPath("$.mobile").isEqualTo("+4512345678")
                .jsonPath("$.avatar").isEqualTo("/api/storage/fetch/k1").jsonPath("$.gender").isEqualTo(2).jsonPath("$.birthday").isEqualTo("1990-05-01")
                .jsonPath("$.username").isEqualTo("alice");
        client.get().uri("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.nickname").isEqualTo("Alice B.").jsonPath("$.birthday").isEqualTo("1990-05-01");
        // without a token: 401; invalid values: 400 with errors; a mobile another member holds: 409
        client.put().uri("/auth/me").contentType(MediaType.APPLICATION_JSON).bodyValue("{\"nickname\":\"x\"}").exchange().expectStatus().isUnauthorized();
        client.put().uri("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"gender\":7,\"birthday\":\"2999-01-01\"}")
                .exchange().expectStatus().isBadRequest().expectBody().jsonPath("$.errors").isArray();
        client.post().uri("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"carol\",\"password\":\"another-secret\",\"mobile\":\"+4500000001\"}")
                .exchange().expectStatus().isCreated();
        client.put().uri("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"nickname\":\"Alice B.\",\"mobile\":\"+4500000001\"}")
                .exchange().expectStatus().isEqualTo(409).expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);
        // clearing the mobile and the birthday
        client.put().uri("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"nickname\":\"Alice B.\",\"mobile\":\"\"}")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.mobile").isEqualTo("").jsonPath("$.birthday").doesNotExist();

        // password: wrong current 401, too short 400, then a change that keeps this session and drops the other device
        client.post().uri("/auth/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + access).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"currentPassword\":\"nope\",\"newPassword\":\"brand-new-secret\"}")
                .exchange().expectStatus().isUnauthorized().expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);
        client.post().uri("/auth/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + access).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"currentPassword\":\"secret123\",\"newPassword\":\"short\"}")
                .exchange().expectStatus().isBadRequest();
        var changed = client.post().uri("/auth/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + access).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"currentPassword\":\"secret123\",\"newPassword\":\"brand-new-secret\"}")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.accessToken").exists().returnResult();
        ResponseCookie renewed = changed.getResponseCookies().getFirst("announce_refresh");
        assertNotNull(renewed);
        client.post().uri("/auth/refresh").cookie("announce_refresh", otherDevice.getValue()).exchange().expectStatus().isUnauthorized();
        client.post().uri("/auth/refresh").cookie("announce_refresh", firstDevice.getValue()).exchange().expectStatus().isUnauthorized();
        client.post().uri("/auth/refresh").cookie("announce_refresh", renewed.getValue()).exchange().expectStatus().isOk();
        client.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"alice\",\"password\":\"secret123\"}").exchange().expectStatus().isUnauthorized();
        client.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"alice\",\"password\":\"brand-new-secret\"}").exchange().expectStatus().isOk();
        // back to the known password for the other tests of this context
        client.post().uri("/auth/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + access).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"currentPassword\":\"brand-new-secret\",\"newPassword\":\"secret123\"}").exchange().expectStatus().isOk();
    }

    @Test
    void badCredentialsAndInvalidBodiesAreProblemDetails() {
        client.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"alice\",\"password\":\"wrong\"}")
                .exchange().expectStatus().isUnauthorized().expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);
        client.post().uri("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"x\",\"password\":\"short\"}")
                .exchange().expectStatus().isBadRequest().expectBody().jsonPath("$.errors").isArray();
        client.post().uri("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"alice\",\"password\":\"another-secret\"}")
                .exchange().expectStatus().isEqualTo(409);
        client.post().uri("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"bob\",\"password\":\"another-secret\",\"nickname\":\"Bob\"}")
                .exchange().expectStatus().isCreated().expectBody().jsonPath("$.user.nickname").isEqualTo("Bob");
    }

    @Test
    void aBrokenTokenIsAnonymous() {
        client.get().uri("/api/orders").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt").exchange().expectStatus().isUnauthorized();
    }
}
