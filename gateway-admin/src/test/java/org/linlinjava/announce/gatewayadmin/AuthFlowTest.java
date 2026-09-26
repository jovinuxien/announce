package org.linlinjava.announce.gatewayadmin;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.linlinjava.announce.db.dao.reactive.AnnounceRefreshTokenMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallAdminMapper;
import org.linlinjava.announce.db.domain.AnnounceRefreshTokenExample;
import org.linlinjava.announce.db.domain.LitemallAdmin;
import org.linlinjava.announce.db.domain.LitemallAdminExample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sign-in against the live {@code announce_reactive_db}: a throw-away administrator (role 1 = super) logs in, reads
 * {@code /auth/me}, rotates the refresh cookie, is refused with the used cookie, logs out and is refused afterwards.
 * The rows the test creates are removed at the end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthFlowTest {

    static final String USERNAME = "gwtest_" + UUID.randomUUID().toString().substring(0, 8);
    static final String PASSWORD = "secret-1";
    static final String COOKIE = "announce_admin_refresh";

    @Autowired WebTestClient client;
    @Autowired LitemallAdminMapper admins;
    @Autowired AnnounceRefreshTokenMapper tokens;

    Integer adminId;
    String accessToken;
    String refreshCookie;

    @BeforeAll
    void createAdmin() {
        LitemallAdmin row = new LitemallAdmin();
        row.setUsername(USERNAME);
        row.setPassword(new BCryptPasswordEncoder().encode(PASSWORD));
        row.setAvatar("");
        row.setRoleIds(new Integer[]{1});
        row.setAddTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        row.setDeleted(false);
        admins.insertSelective(row).block();
        adminId = row.getId();
        assertThat(adminId).isNotNull();
    }

    @AfterAll
    void deleteAdmin() {
        AnnounceRefreshTokenExample t = new AnnounceRefreshTokenExample();
        t.or().andUserTypeEqualTo("ADMIN").andUserIdEqualTo(adminId);
        tokens.deleteByExample(t).block();
        LitemallAdminExample a = new LitemallAdminExample();
        a.or().andUsernameEqualTo(USERNAME);
        admins.deleteByExample(a).block();
    }

    private static String cookieValue(EntityExchangeResult<?> result) {
        ResponseCookie c = result.getResponseCookies().getFirst(COOKIE);
        assertThat(c).as("refresh cookie").isNotNull();
        assertThat(c.isHttpOnly()).isTrue();
        assertThat(c.getPath()).isEqualTo("/auth");
        assertThat(c.getSameSite()).isEqualTo("Strict");
        return c.getValue();
    }

    @Test
    @Order(1)
    void wrongPasswordIsA401Problem() {
        client.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"" + USERNAME + "\",\"password\":\"nope\"}").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.detail").isEqualTo("invalid username or password");
    }

    @Test
    @Order(2)
    void loginReturnsAnAccessTokenAndSetsTheRefreshCookie() {
        EntityExchangeResult<byte[]> result = client.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.tokenType").isEqualTo("Bearer").jsonPath("$.expiresIn").isEqualTo(7200)
                .jsonPath("$.admin.username").isEqualTo(USERNAME).jsonPath("$.admin.roleIds[0]").isEqualTo(1)
                .jsonPath("$.admin.roles").value(org.hamcrest.Matchers.hasItems("ROLE_ADMIN", "ROLE_SUPER"))
                .jsonPath("$.accessToken").isNotEmpty()
                .returnResult();
        refreshCookie = cookieValue(result);
        String body = new String(result.getResponseBody());
        int i = body.indexOf("\"accessToken\":\"") + 15;
        accessToken = body.substring(i, body.indexOf('"', i));

        client.get().uri("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.id").isEqualTo(adminId).jsonPath("$.username").isEqualTo(USERNAME);
        LitemallAdmin stored = admins.selectByPrimaryKey(adminId).block();
        assertThat(stored.getLastLoginTime()).as("last login recorded").isNotNull();
    }

    @Test
    @Order(3)
    void refreshRotatesTheCookieAndTheUsedCookieIsRefused() {
        EntityExchangeResult<byte[]> rotated = client.post().uri("/auth/refresh").cookie(COOKIE, refreshCookie).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.accessToken").isNotEmpty().jsonPath("$.admin.id").isEqualTo(adminId)
                .returnResult();
        String next = cookieValue(rotated);
        assertThat(next).isNotEqualTo(refreshCookie);

        client.post().uri("/auth/refresh").cookie(COOKIE, refreshCookie).exchange().expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.type").isEqualTo("urn:announce:auth:invalid-refresh-token");
        refreshCookie = next;
    }

    @Test
    @Order(4)
    void logoutRevokesTheRefreshTokenAndClearsTheCookie() {
        EntityExchangeResult<byte[]> out = client.post().uri("/auth/logout").cookie(COOKIE, refreshCookie).exchange()
                .expectStatus().isNoContent().expectBody().returnResult();
        ResponseCookie cleared = out.getResponseCookies().getFirst(COOKIE);
        assertThat(cleared).isNotNull();
        assertThat(cleared.getMaxAge().isZero()).isTrue();
        client.post().uri("/auth/refresh").cookie(COOKIE, refreshCookie).exchange().expectStatus().isUnauthorized();
        // the access token itself stays valid until it expires (stateless); the SPA drops it on logout
        client.get().uri("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).exchange().expectStatus().isOk();
    }
}
