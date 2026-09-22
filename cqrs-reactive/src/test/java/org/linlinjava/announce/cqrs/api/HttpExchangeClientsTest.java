package org.linlinjava.announce.cqrs.api;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.cqrs.CqrsProperties;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigurationProperties;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class HttpExchangeClientsTest {

    @HttpExchange("/api/announces")
    interface AnnounceApi {
        @GetExchange("/{id}")
        Mono<String> get(@PathVariable String id);
    }

    static DisposableServer server;
    static final AtomicInteger requests = new AtomicInteger();

    @BeforeAll
    static void start() {
        server = HttpServer.create().port(0).route(routes -> routes.get("/api/announces/{id}", (request, response) -> {
            requests.incrementAndGet();
            if ("boom".equals(request.param("id"))) {
                return response.status(500).send();
            }
            return response.sendString(Mono.just("announce-" + request.param("id")));
        })).bindNow();
    }

    @AfterAll
    static void stop() {
        server.disposeNow();
    }

    private static CqrsProperties.Api pinned(String serviceId) {
        CqrsProperties.Api api = new CqrsProperties.Api();
        CqrsProperties.Api.Client client = new CqrsProperties.Api.Client();
        client.setBaseUrl("http://localhost:" + server.port());
        api.getClients().put(serviceId, client);
        return api;
    }

    private static ReactiveResilience4JCircuitBreakerFactory breakers() {
        return new ReactiveResilience4JCircuitBreakerFactory(CircuitBreakerRegistry.ofDefaults(), TimeLimiterRegistry.ofDefaults(),
                new Resilience4JConfigurationProperties());
    }

    @Test
    void pinnedBaseUrlBypassesDiscovery() {
        ExchangeFilterFunction loadBalancer = (request, next) -> Mono.error(new AssertionError("load balancer must not be used"));
        AnnounceApi api = new HttpExchangeClients(WebClient.builder(), loadBalancer, breakers(), pinned("announce-management"))
                .create(AnnounceApi.class, "announce-management");

        StepVerifier.create(api.get("7")).expectNext("announce-7").verifyComplete();
    }

    @Test
    void unpinnedClientGoesThroughTheLoadBalancerFilter() {
        ExchangeFilterFunction loadBalancer = (request, next) -> {
            assertThat(request.url().getHost()).isEqualTo("announce-management");
            URI resolved = URI.create("http://localhost:" + server.port() + request.url().getPath());
            return next.exchange(ClientRequest.from(request).url(resolved).build());
        };
        AnnounceApi api = new HttpExchangeClients(WebClient.builder(), loadBalancer, breakers(), new CqrsProperties.Api())
                .create(AnnounceApi.class, "announce-management");

        StepVerifier.create(api.get("8")).expectNext("announce-8").verifyComplete();
    }

    @Test
    void circuitBreakerOpensAfterRepeatedFailures() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        ReactiveResilience4JCircuitBreakerFactory factory = new ReactiveResilience4JCircuitBreakerFactory(registry,
                TimeLimiterRegistry.ofDefaults(), new Resilience4JConfigurationProperties());
        factory.configureDefault(id -> new org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .slidingWindowSize(2).minimumNumberOfCalls(2).failureRateThreshold(50).build())
                .timeLimiterConfig(io.github.resilience4j.timelimiter.TimeLimiterConfig.ofDefaults())
                .build());
        AnnounceApi api = new HttpExchangeClients(WebClient.builder(), null, factory, pinned("flaky")).create(AnnounceApi.class, "flaky");

        int before = requests.get();
        for (int i = 0; i < 2; i++) {
            StepVerifier.create(api.get("boom")).expectError().verify();
        }
        assertThat(registry.circuitBreaker("flaky").getState()).isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN);
        StepVerifier.create(api.get("boom")).expectErrorSatisfies(error -> assertThat(error)
                .isInstanceOf(org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException.class)
                .hasCauseInstanceOf(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class)).verify();
        assertThat(requests.get() - before).isEqualTo(2);
    }
}
