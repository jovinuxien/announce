package org.linlinjava.announce.cqrs.observability;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

class CorrelationWebFilterTest {

    @RestController
    static class EchoController {
        @GetMapping("/echo")
        Mono<String> echo() {
            return Mono.deferContextual(context -> Mono.just(CorrelationContext.correlationId(context).orElse("none")));
        }
    }

    private final WebTestClient client = WebTestClient.bindToController(new EchoController())
            .webFilter(new CorrelationWebFilter("X-Correlation-Id")).build();

    @Test
    void propagatesIncomingHeaderIntoContextAndResponse() {
        client.get().uri("/echo").header("X-Correlation-Id", "abc-123").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", "abc-123")
                .expectBody(String.class).isEqualTo("abc-123");
    }

    @Test
    void generatesAnIdWhenMissing() {
        client.get().uri("/echo").exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Correlation-Id")
                .expectBody(String.class).value(body -> org.assertj.core.api.Assertions.assertThat(body).isNotEqualTo("none").hasSize(36));
    }
}
