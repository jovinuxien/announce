package org.linlinjava.announce.gatewayapi.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.linlinjava.announce.gatewayapi.auth.AccessTokens;
import org.linlinjava.announce.gatewayapi.auth.Account;
import org.linlinjava.announce.gatewayapi.auth.InMemoryAuthStores;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Picture upload and fetch at the edge: member-only upload of images, public fetch, refusals as problem details. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(InMemoryAuthStores.class)
class StorageRoutesTest {

    @TempDir static Path dir;
    @LocalServerPort int port;
    @Autowired AccessTokens accessTokens;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry r) {
        r.add("announce.storage.dir", () -> dir.toString());
        r.add("announce.storage.max-size", () -> "2KB");
        r.add("announce.routes.announce-management", () -> "http://localhost:1");
        r.add("announce.routes.order", () -> "http://localhost:1");
        r.add("announce.routes.messaging", () -> "http://localhost:1");
        r.add("announce.routes.messaging-ws", () -> "ws://localhost:1");
        r.add("announce.search.searcher-url", () -> "http://localhost:1");
        r.add("announce.search.suggest-url", () -> "http://localhost:1");
    }

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).responseTimeout(Duration.ofSeconds(10)).build();
    }

    private String token() {
        return accessTokens.issue(new Account(InMemoryAuthStores.USER_ID, InMemoryAuthStores.USERNAME, "", "Alice", "", "", true)).token();
    }

    private static MultipartBodyBuilder image(String name, String type, int bytes) {
        MultipartBodyBuilder b = new MultipartBodyBuilder();
        b.part("file", new ByteArrayResource(new byte[bytes]) {
            @Override
            public String getFilename() {
                return name;
            }
        }).contentType(MediaType.parseMediaType(type));
        return b;
    }

    @Test
    void memberUploadsAnImageAndEveryoneCanFetchIt() throws Exception {
        var res = client().post().uri("/api/storage/upload").header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                .contentType(MediaType.MULTIPART_FORM_DATA).bodyValue(image("bike.png", "image/png", 512).build())
                .exchange().expectStatus().isCreated()
                .expectBody().jsonPath("$.type").isEqualTo("image/png").jsonPath("$.size").isEqualTo(512).jsonPath("$.name").isEqualTo("bike.png")
                .returnResult();
        String body = new String(res.getResponseBody());
        String key = com.jayway.jsonpath.JsonPath.read(body, "$.key");
        String url = com.jayway.jsonpath.JsonPath.read(body, "$.url");
        assertEquals("/api/storage/fetch/" + key, url);
        assertTrue(key.endsWith(".png"));
        assertTrue(Files.isRegularFile(dir.resolve(key)));

        byte[] fetched = client().get().uri(url).exchange().expectStatus().isOk()
                .expectHeader().contentType(MediaType.IMAGE_PNG)
                .expectHeader().valueMatches(HttpHeaders.CACHE_CONTROL, ".*max-age=2592000.*")
                .expectBody(byte[].class).returnResult().getResponseBody();
        assertNotNull(fetched);
        assertEquals(512, fetched.length);
    }

    @Test
    void refusals() {
        client().post().uri("/api/storage/upload").contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(image("bike.png", "image/png", 10).build()).exchange().expectStatus().isUnauthorized();
        client().post().uri("/api/storage/upload").header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                .contentType(MediaType.MULTIPART_FORM_DATA).bodyValue(image("notes.txt", "text/plain", 10).build())
                .exchange().expectStatus().isEqualTo(415).expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);
        client().post().uri("/api/storage/upload").header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                .contentType(MediaType.MULTIPART_FORM_DATA).bodyValue(image("big.jpg", "image/jpeg", 4096).build())
                .exchange().expectStatus().isEqualTo(413);
        client().get().uri("/api/storage/fetch/00000000000000000000000000000000.png").exchange().expectStatus().isNotFound();
        // an encoded slash is refused by the framework (400) before the key pattern (404) gets a say
        client().get().uri("/api/storage/fetch/..%2Fapplication.yml").exchange().expectStatus().value(v -> assertTrue(v == 400 || v == 404));
    }
}
