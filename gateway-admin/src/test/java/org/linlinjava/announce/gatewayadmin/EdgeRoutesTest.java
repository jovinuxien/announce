package org.linlinjava.announce.gatewayadmin;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.gatewayadmin.auth.AdminPrincipal;
import org.linlinjava.announce.gatewayadmin.jwt.AdminJwtService;
import org.linlinjava.announce.gatewayadmin.machine.MachineTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The edge in front of a MockWebServer standing in for announce-management: the authorisation matrix, identity
 * forwarding and anti-spoofing, the machine token, the SPA fallback and the public surface.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EdgeRoutesTest {

    static MockWebServer backend;

    @BeforeAll
    static void startBackend() throws IOException {
        backend = new MockWebServer();
        backend.start();
    }

    @AfterAll
    static void stopBackend() throws IOException {
        backend.shutdown();
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("announce.routes.announce-management", () -> "http://localhost:" + backend.getPort());
    }

    @Autowired WebTestClient client;
    @Autowired AdminJwtService jwt;
    @Autowired MachineTokens machineTokens;

    String adminToken() {
        return jwt.issue(new AdminPrincipal(42, "moderator", "", List.of(2), Set.of(AdminPrincipal.ROLE_ADMIN)));
    }

    String superToken() {
        return jwt.issue(new AdminPrincipal(1, "admin123", "", List.of(1), Set.of(AdminPrincipal.ROLE_ADMIN, AdminPrincipal.ROLE_SUPER)));
    }

    /** A member token signed with the same key (what the customer edge would mint if the pair were shared). */
    String memberToken() {
        return jwt.issue("7", "MEMBER", 7, "buyer", "", List.of("ROLE_MEMBER"), List.of());
    }

    private RecordedRequest lastBackendRequest() throws InterruptedException {
        RecordedRequest request = backend.takeRequest(5, TimeUnit.SECONDS);
        assertThat(request).as("the backend received the proxied request").isNotNull();
        return request;
    }

    @Test
    void withoutATokenTheApiIs401AsAProblem() {
        int before = backend.getRequestCount();
        client.post().uri("/api/admin/listings/import").contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.status").isEqualTo(401).jsonPath("$.type").isEqualTo("urn:announce:edge:unauthenticated");
        client.get().uri("/auth/me").exchange().expectStatus().isUnauthorized();
        assertThat(backend.getRequestCount()).isEqualTo(before);
    }

    @Test
    void aForgedOrExpiredTokenIs401() {
        client.get().uri("/api/admin/announces").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.token").exchange()
                .expectStatus().isUnauthorized().expectBody().jsonPath("$.type").isEqualTo("urn:announce:edge:invalid-token");
    }

    @Test
    void aMemberTokenIs403OnTheAdminApi() {
        int before = backend.getRequestCount();
        client.get().uri("/api/admin/announces").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken()).exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        client.get().uri("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken()).exchange().expectStatus().isForbidden();
        assertThat(backend.getRequestCount()).isEqualTo(before);
    }

    @Test
    void anAdminTokenIsForwardedAsIdentityHeadersAndSpoofedHeadersAreDropped() throws InterruptedException {
        backend.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody("{\"items\":[],\"total\":0}"));

        client.get().uri("/api/admin/announces?status=PUBLISHED")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .header("X-User-Id", "1").header("X-User-Roles", "ROLE_SUPER").header("X-Seller-Id", "1").header("X-Service-Token", "forged")
                .cookie("announce_admin_refresh", "secret")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.total").isEqualTo(0);

        RecordedRequest proxied = lastBackendRequest();
        assertThat(proxied.getPath()).isEqualTo("/api/admin/announces?status=PUBLISHED");
        assertThat(proxied.getHeader("X-User-Id")).isEqualTo("42");
        assertThat(proxied.getHeader("X-User-Type")).isEqualTo("ADMIN");
        assertThat(proxied.getHeader("X-User-Roles")).isEqualTo("ROLE_ADMIN");
        assertThat(proxied.getHeaders().values("X-User-Id")).hasSize(1);
        assertThat(proxied.getHeader("X-Seller-Id")).isNull();
        assertThat(proxied.getHeader(HttpHeaders.AUTHORIZATION)).as("the staff JWT is never relayed").isNull();
        assertThat(proxied.getHeader(HttpHeaders.COOKIE)).as("the refresh cookie never leaves the edge").isNull();
        String serviceToken = proxied.getHeader("X-Service-Token");
        assertThat(serviceToken).isNotEqualTo("forged");
        assertThat(machineTokens.verify(serviceToken, 60)).as("machine token signed by the edge").isTrue();
    }

    @Test
    void writesReachTheBackendWithTheirBodyAndProblemsPassThrough() throws InterruptedException {
        backend.enqueue(new MockResponse().setResponseCode(409).setHeader("Content-Type", "application/problem+json")
                .setBody("{\"status\":409,\"code\":\"not-published\",\"detail\":\"Only a published announce can be moderated\"}"));
        client.post().uri("/api/admin/announces/a1/withdraw").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"reason\":\"spam\"}").exchange()
                .expectStatus().isEqualTo(409)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.code").isEqualTo("not-published");
        RecordedRequest proxied = lastBackendRequest();
        assertThat(proxied.getMethod()).isEqualTo("POST");
        assertThat(proxied.getBody().readUtf8()).isEqualTo("{\"reason\":\"spam\"}");
        assertThat(proxied.getHeader("X-User-Id")).isEqualTo("42");
    }

    @Test
    void catalogWritesAreRoutedToAnnounceManagement() throws InterruptedException {
        backend.enqueue(new MockResponse().setResponseCode(201).setHeader("Content-Type", "application/json").setBody("{\"id\":9,\"kind\":\"JOB\"}"));
        client.post().uri("/api/categories").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"kind\":\"JOB\",\"name\":\"IT\"}").exchange().expectStatus().isCreated();
        assertThat(lastBackendRequest().getPath()).isEqualTo("/api/categories");
    }

    @Test
    void superOnlyPathsRefuseAPlainAdministrator() {
        int before = backend.getRequestCount();
        client.get().uri("/api/admin/admins").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()).exchange()
                .expectStatus().isForbidden().expectBody().jsonPath("$.type").isEqualTo("urn:announce:edge:forbidden");
        client.get().uri("/api/admin/roles").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()).exchange().expectStatus().isForbidden();
        client.get().uri("/api/admin/permissions").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()).exchange().expectStatus().isForbidden();
        // the same paths answer a super administrator from the edge itself (no backend involved)
        client.get().uri("/api/admin/permissions").header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken()).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$[0].code").isEqualTo("*");
        assertThat(backend.getRequestCount()).isEqualTo(before);
    }

    @Test
    void meAnswersFromTheToken() {
        client.get().uri("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken()).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.id").isEqualTo(1).jsonPath("$.username").isEqualTo("admin123")
                .jsonPath("$.roles").value(org.hamcrest.Matchers.hasItems("ROLE_ADMIN", "ROLE_SUPER"));
    }

    @Test
    void theSpaShellIsPublicAndDeepLinksFallBackToIt() {
        client.get().uri("/").exchange().expectStatus().isOk().expectBody(String.class).value(b -> assertThat(b).contains("admin test shell"));
        client.get().uri("/announces/abc").exchange().expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class).value(b -> assertThat(b).contains("admin test shell"));
        client.get().uri("/admins/new?tab=roles").exchange().expectStatus().isOk();
        client.get().uri("/static/js/missing.js").exchange().expectStatus().isNotFound();
        client.get().uri("/main.abc123.js").exchange().expectStatus().isNotFound();
        // a backend prefix is never rewritten to the shell
        client.get().uri("/api/nothing").exchange().expectStatus().isUnauthorized();
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void loginIsPublicAndValidatesItsBody() {
        client.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON).bodyValue("{\"username\":\"\",\"password\":\"\"}").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.type").isEqualTo("urn:announce:auth:bad-credentials");
        client.post().uri("/auth/refresh").exchange().expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.type").isEqualTo("urn:announce:auth:invalid-refresh-token");
        client.post().uri("/auth/logout").exchange().expectStatus().isNoContent();
    }
}
