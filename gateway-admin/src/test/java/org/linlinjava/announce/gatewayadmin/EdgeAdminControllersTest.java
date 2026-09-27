package org.linlinjava.announce.gatewayadmin;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.linlinjava.announce.db.dao.reactive.LitemallAdminMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallPermissionMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallRegionMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallRoleMapper;
import org.linlinjava.announce.db.domain.LitemallAdminExample;
import org.linlinjava.announce.db.domain.LitemallPermissionExample;
import org.linlinjava.announce.db.domain.LitemallRegionExample;
import org.linlinjava.announce.db.domain.LitemallRoleExample;
import org.linlinjava.announce.gatewayadmin.auth.AdminPrincipal;
import org.linlinjava.announce.gatewayadmin.jwt.AdminJwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The edge-hosted CRUD against the live {@code announce_reactive_db}: a role with permissions, an administrator
 * holding it, the guards (role in use, self delete, duplicate names), regions, the member list and the dashboard.
 * Every row created here carries the {@code gwtest_} prefix and is deleted at the end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EdgeAdminControllersTest {

    static final String TAG = "gwtest_" + UUID.randomUUID().toString().substring(0, 8);

    @Autowired WebTestClient client;
    @Autowired AdminJwtService jwt;
    @Autowired LitemallRoleMapper roles;
    @Autowired LitemallPermissionMapper permissions;
    @Autowired LitemallAdminMapper admins;
    @Autowired LitemallRegionMapper regions;

    Integer roleId;
    Integer adminId;
    Integer regionId;

    String superToken() {
        return jwt.issue(new AdminPrincipal(1, "admin123", "", List.of(1), Set.of(AdminPrincipal.ROLE_ADMIN, AdminPrincipal.ROLE_SUPER)));
    }

    WebTestClient.RequestBodySpec post(String uri) {
        return client.post().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken()).contentType(MediaType.APPLICATION_JSON);
    }

    WebTestClient.RequestBodySpec put(String uri) {
        return client.put().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken()).contentType(MediaType.APPLICATION_JSON);
    }

    WebTestClient.ResponseSpec get(String uri) {
        return client.get().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken()).exchange();
    }

    WebTestClient.ResponseSpec delete(String uri) {
        return client.delete().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken()).exchange();
    }

    static int idOf(byte[] body) {
        String json = new String(body);
        int i = json.indexOf("\"id\":") + 5;
        return Integer.parseInt(json.substring(i, json.indexOf(',', i)).trim());
    }

    @AfterAll
    void cleanUp() {
        LitemallAdminExample a = new LitemallAdminExample();
        a.or().andUsernameLike(TAG + "%");
        admins.deleteByExample(a).block();
        LitemallRoleExample r = new LitemallRoleExample();
        r.or().andNameLike(TAG + "%");
        roles.selectByExample(r).flatMap(role -> {
            LitemallPermissionExample p = new LitemallPermissionExample();
            p.or().andRoleIdEqualTo(role.getId());
            return permissions.deleteByExample(p);
        }).blockLast();
        roles.deleteByExample(r).block();
        LitemallRegionExample g = new LitemallRegionExample();
        g.or().andNameLike(TAG + "%");
        regions.deleteByExample(g).block();
    }

    @Test
    @Order(1)
    void aRoleIsCreatedWithPermissions() {
        byte[] created = post("/api/admin/roles").bodyValue("{\"name\":\"" + TAG + "-moderators\",\"description\":\"test role\",\"enabled\":true}")
                .exchange().expectStatus().isCreated().expectBody().jsonPath("$.name").isEqualTo(TAG + "-moderators").jsonPath("$.enabled").isEqualTo(true)
                .returnResult().getResponseBody();
        roleId = idOf(created);

        post("/api/admin/roles").bodyValue("{\"name\":\"" + TAG + "-moderators\"}").exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("role-name-taken");
        post("/api/admin/roles").bodyValue("{\"name\":\"\"}").exchange().expectStatus().isBadRequest();

        put("/api/admin/roles/" + roleId + "/permissions").bodyValue("{\"permissions\":[\"admin:announce:moderate\",\"admin:catalog:manage\"]}")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.length()").isEqualTo(2);
        put("/api/admin/roles/" + roleId + "/permissions").bodyValue("{\"permissions\":[\"admin:catalog:manage\",\"admin:member:manage\"]}")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$").value(org.hamcrest.Matchers.containsInAnyOrder("admin:catalog:manage", "admin:member:manage"));
        put("/api/admin/roles/" + roleId + "/permissions").bodyValue("{\"permissions\":[\"admin:does:not:exist\"]}").exchange().expectStatus().isBadRequest();
        get("/api/admin/roles?q=" + TAG).expectStatus().isOk().expectBody().jsonPath("$.total").isEqualTo(1);
        get("/api/admin/roles/all").expectStatus().isOk().expectBody().jsonPath("$[?(@.id == " + roleId + ")].name").isEqualTo(TAG + "-moderators");
        get("/api/admin/permissions").expectStatus().isOk().expectBody().jsonPath("$[0].code").isEqualTo("*");
    }

    @Test
    @Order(2)
    void anAdministratorIsCreatedWithTheRoleAndNeverExposesThePassword() {
        byte[] created = post("/api/admin/admins").bodyValue("{\"username\":\"" + TAG + "\",\"password\":\"secret-1\",\"roleIds\":[" + roleId + "]}")
                .exchange().expectStatus().isCreated()
                .expectBody().jsonPath("$.username").isEqualTo(TAG).jsonPath("$.roleIds[0]").isEqualTo(roleId).jsonPath("$.password").doesNotExist()
                .returnResult().getResponseBody();
        adminId = idOf(created);
        assertThat(admins.selectByPrimaryKey(adminId).block().getPassword()).startsWith("$2a$");

        post("/api/admin/admins").bodyValue("{\"username\":\"" + TAG + "\",\"password\":\"secret-1\"}").exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("username-taken");
        post("/api/admin/admins").bodyValue("{\"username\":\"x\",\"password\":\"secret-1\"}").exchange().expectStatus().isBadRequest();
        post("/api/admin/admins").bodyValue("{\"username\":\"" + TAG + "b\",\"password\":\"123\"}").exchange().expectStatus().isBadRequest();

        // the new account can sign in through the edge with the role it holds (ROLE_ADMIN, not super)
        client.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON).bodyValue("{\"username\":\"" + TAG + "\",\"password\":\"secret-1\"}")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.admin.roles").value(org.hamcrest.Matchers.contains("ROLE_ADMIN"));

        get("/api/admin/admins?q=" + TAG).expectStatus().isOk().expectBody().jsonPath("$.total").isEqualTo(1).jsonPath("$.items[0].password").doesNotExist();
        put("/api/admin/admins/" + adminId).bodyValue("{\"username\":\"" + TAG + "\",\"avatar\":\"http://x/a.png\",\"roleIds\":[" + roleId + "],\"password\":\"secret-2\"}")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.avatar").isEqualTo("http://x/a.png");
        client.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON).bodyValue("{\"username\":\"" + TAG + "\",\"password\":\"secret-2\"}")
                .exchange().expectStatus().isOk();

        // the role is in use, the super cannot delete themself
        delete("/api/admin/roles/" + roleId).expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("role-in-use");
        delete("/api/admin/admins/1").expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("self-delete");
    }

    @Test
    @Order(3)
    void deletingTheAdministratorFreesTheRole() {
        delete("/api/admin/admins/" + adminId).expectStatus().isNoContent();
        get("/api/admin/admins/" + adminId).expectStatus().isNotFound();
        delete("/api/admin/roles/" + roleId).expectStatus().isNoContent();
        get("/api/admin/roles/" + roleId).expectStatus().isNotFound();
    }

    @Test
    @Order(4)
    void regionsAreManaged() {
        byte[] created = post("/api/admin/regions").bodyValue("{\"pid\":0,\"name\":\"" + TAG + "-land\",\"type\":1,\"code\":999001}")
                .exchange().expectStatus().isCreated().expectBody().jsonPath("$.name").isEqualTo(TAG + "-land").returnResult().getResponseBody();
        regionId = idOf(created);
        byte[] child = post("/api/admin/regions").bodyValue("{\"pid\":" + regionId + ",\"name\":\"" + TAG + "-town\",\"type\":2}")
                .exchange().expectStatus().isCreated().expectBody().returnResult().getResponseBody();
        int childId = idOf(child);
        get("/api/admin/regions?pid=" + regionId).expectStatus().isOk().expectBody().jsonPath("$.total").isEqualTo(1).jsonPath("$.items[0].id").isEqualTo(childId);
        put("/api/admin/regions/" + childId).bodyValue("{\"name\":\"" + TAG + "-city\",\"type\":2}").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.name").isEqualTo(TAG + "-city").jsonPath("$.pid").isEqualTo(regionId);
        delete("/api/admin/regions/" + regionId).expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("region-has-children");
        delete("/api/admin/regions/" + childId).expectStatus().isNoContent();
        delete("/api/admin/regions/" + regionId).expectStatus().isNoContent();
        post("/api/admin/regions").bodyValue("{\"name\":\"x\",\"type\":9}").exchange().expectStatus().isBadRequest();
    }

    @Test
    @Order(5)
    void membersDashboardAndListingStatsAnswer() {
        get("/api/admin/users?page=1&size=5").expectStatus().isOk().expectBody().jsonPath("$.page").isEqualTo(1).jsonPath("$.items").isArray();
        get("/api/admin/users?q=zzz-nobody-zzz").expectStatus().isOk().expectBody().jsonPath("$.total").isEqualTo(0);
        get("/api/admin/users/999999999").expectStatus().isNotFound();
        get("/api/admin/dashboard?days=7").expectStatus().isOk()
                .expectBody().jsonPath("$.announces").isArray().jsonPath("$.listings").isArray()
                .jsonPath("$.members").isNumber().jsonPath("$.admins").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1))
                .jsonPath("$.categories").isNumber().jsonPath("$.generatedAt").isNotEmpty();
        get("/api/admin/listing-stats?limit=3").expectStatus().isOk()
                .expectBody().jsonPath("$.counts").isArray().jsonPath("$.recentGone").isArray().jsonPath("$.topClicked").isArray();
        // a plain administrator may read these, only the super paths are closed to them
        String adminToken = jwt.issue(new AdminPrincipal(42, "moderator", "", List.of(2), Set.of(AdminPrincipal.ROLE_ADMIN)));
        client.get().uri("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).exchange().expectStatus().isOk();
    }
}
