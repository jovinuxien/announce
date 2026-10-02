package org.linlinjava.announce.cqrs.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceTokenWebFilterTest {

    static final String SECRET = ServiceTokensTest.SECRET;
    static final Clock NOW = Clock.fixed(Instant.ofEpochSecond(ServiceTokensTest.EPOCH), ZoneOffset.UTC);

    @RestController
    static class WhoController {
        @GetMapping("/api/who")
        Mono<String> who(@RequestHeader(value = "X-User-Id", required = false) String user,
                         @RequestAttribute(value = "org.linlinjava.announce.cqrs.security.ServiceTokenWebFilter.caller", required = false) String caller) {
            return Mono.just("user=" + user + " via=" + caller);
        }

        @GetMapping("/actuator/health")
        Mono<String> health() {
            return Mono.just("UP");
        }
    }

    private static ServiceTokenProperties properties(String mode) {
        ServiceTokenProperties properties = new ServiceTokenProperties();
        properties.setMode(mode);
        properties.setSecret(SECRET);
        return properties;
    }

    private static WebTestClient client(ServiceTokenProperties properties) {
        return WebTestClient.bindToController(new WhoController()).webFilter(new ServiceTokenWebFilter(properties, NOW)).build();
    }

    private static String token(long epochSeconds) {
        return ServiceTokens.mint("gateway-api", SECRET, epochSeconds);
    }

    @Test
    void enforceServesARequestWithAFreshTokenAndExposesTheCaller() {
        client(properties("enforce")).get().uri("/api/who")
                .header(ServiceTokens.HEADER, token(ServiceTokensTest.EPOCH - 5)).header("X-User-Id", "7").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("user=7 via=gateway-api");
    }

    @Test
    void enforceRefusesARequestWithoutToken() {
        client(properties("enforce")).get().uri("/api/who").header("X-User-Id", "7").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.type").isEqualTo(ServiceTokenWebFilter.PROBLEM_TYPE_MISSING)
                .jsonPath("$.status").isEqualTo(401);
    }

    @Test
    void enforceRefusesAStaleOrForgedToken() {
        WebTestClient client = client(properties("enforce"));
        client.get().uri("/api/who").header(ServiceTokens.HEADER, token(ServiceTokensTest.EPOCH - 61)).exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.type").isEqualTo(ServiceTokenWebFilter.PROBLEM_TYPE_INVALID);
        client.get().uri("/api/who")
                .header(ServiceTokens.HEADER, ServiceTokens.mint("gateway-api", "guessed", ServiceTokensTest.EPOCH)).exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void openPathsNeedNoToken() {
        client(properties("enforce")).get().uri("/actuator/health").exchange()
                .expectStatus().isOk().expectBody(String.class).isEqualTo("UP");

        ServiceTokenProperties closed = properties("enforce");
        closed.setOpenPaths(List.of());
        client(closed).get().uri("/actuator/health").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void logModeServesTheRequestAnyway() {
        client(properties("log")).get().uri("/api/who").header("X-User-Id", "7").exchange()
                .expectStatus().isOk().expectBody(String.class).isEqualTo("user=7 via=null");
    }

    @Test
    void offModeChecksNothingAndYamlFalseMeansOff() {
        client(properties("off")).get().uri("/api/who").exchange().expectStatus().isOk();
        // an unquoted YAML `off` arrives as the boolean false
        client(properties("false")).get().uri("/api/who").exchange().expectStatus().isOk();
    }

    @Test
    void aCheckingModeWithoutSecretFailsAtStartUp() {
        ServiceTokenProperties properties = properties("enforce");
        properties.setSecret(" ");
        assertThatThrownBy(() -> new ServiceTokenWebFilter(properties, NOW)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANNOUNCE_SERVICE_SECRET");
        assertThatThrownBy(() -> properties("strict").resolvedMode()).isInstanceOf(IllegalStateException.class);
    }
}
