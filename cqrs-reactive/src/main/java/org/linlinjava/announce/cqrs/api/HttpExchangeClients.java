package org.linlinjava.announce.cqrs.api;

import org.linlinjava.announce.cqrs.CqrsProperties;
import org.linlinjava.announce.cqrs.security.ServiceTokenMinter;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Turns an {@link HttpExchange @HttpExchange} interface (the service descriptor, kept in the provider's
 * {@code <name>-api} module) into a client that is
 * <ul>
 *   <li>discovered and load-balanced: the base URL is {@code http://<serviceId>} resolved through Spring Cloud
 *       LoadBalancer (Eureka), unless {@code cqrs.api.clients.<serviceId>.base-url} pins a fixed URL, as for
 *       services outside the registry such as the OCS search tier;</li>
 *   <li>wrapped in a Resilience4j circuit breaker and time limiter named after the service id
 *       ({@code resilience4j.circuitbreaker.instances.<serviceId>.*}); connection failures, timeouts and
 *       5xx answers count as failures, 4xx answers do not;</li>
 *   <li>traced and metered through the auto-configured {@link WebClient.Builder};</li>
 *   <li>authenticated as one of our services when {@code cqrs.api.clients.<serviceId>.service-token=true}: every
 *       call then carries a fresh {@code X-Service-Token} (see {@link ServiceTokenMinter}).</li>
 * </ul>
 *
 * <pre>{@code
 * @Bean
 * AnnounceApi announceApi(HttpExchangeClients clients) {
 *     return clients.create(AnnounceApi.class, "announce-management");
 * }
 * }</pre>
 */
public class HttpExchangeClients {

    private final WebClient.Builder webClientBuilder;
    private final ExchangeFilterFunction loadBalancerFilter;
    private final ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final CqrsProperties.Api properties;
    private final ServiceTokenMinter serviceTokens;

    public HttpExchangeClients(WebClient.Builder webClientBuilder, ExchangeFilterFunction loadBalancerFilter,
                               ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory, CqrsProperties.Api properties) {
        this(webClientBuilder, loadBalancerFilter, circuitBreakerFactory, properties, null);
    }

    /**
     * @param webClientBuilder      the Boot-provided builder (observation-instrumented); a fresh clone is used per client
     * @param loadBalancerFilter    the LoadBalancer exchange filter, or {@code null} when discovery is not configured
     * @param circuitBreakerFactory the Resilience4j factory, or {@code null} to build clients without a breaker
     * @param properties            per service-id overrides
     * @param serviceTokens         mints {@code X-Service-Token} for the clients that opt in, or {@code null}
     */
    public HttpExchangeClients(WebClient.Builder webClientBuilder, ExchangeFilterFunction loadBalancerFilter,
                               ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory, CqrsProperties.Api properties,
                               ServiceTokenMinter serviceTokens) {
        this.webClientBuilder = Objects.requireNonNull(webClientBuilder, "webClientBuilder");
        this.loadBalancerFilter = loadBalancerFilter;
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.properties = Objects.requireNonNull(properties, "properties");
        this.serviceTokens = serviceTokens;
    }

    public <T> T create(Class<T> exchangeInterface, String serviceId) {
        return HttpServiceProxyFactory.builderFor(WebClientAdapter.create(webClient(serviceId))).build()
                .createClient(exchangeInterface);
    }

    /** The configured {@link WebClient} for a service, for callers that need it directly. */
    public WebClient webClient(String serviceId) {
        Objects.requireNonNull(serviceId, "serviceId");
        CqrsProperties.Api.Client overrides = properties.getClients().get(serviceId);
        String baseUrl = overrides != null && overrides.getBaseUrl() != null ? overrides.getBaseUrl() : null;
        WebClient.Builder builder = webClientBuilder.clone();
        if (baseUrl != null) {
            builder.baseUrl(baseUrl);
        } else {
            builder.baseUrl("http://" + serviceId);
            if (loadBalancerFilter != null) {
                builder.filter(loadBalancerFilter);
            }
        }
        if (overrides != null && overrides.isServiceToken()) {
            if (serviceTokens == null || !serviceTokens.configured()) {
                throw new IllegalStateException("cqrs.api.clients." + serviceId + ".service-token=true needs "
                        + "announce.service-token.secret (ANNOUNCE_SERVICE_SECRET)");
            }
            builder.filter(serviceTokens.exchangeFilter());
        }
        if (circuitBreakerFactory != null) {
            ReactiveCircuitBreaker breaker = circuitBreakerFactory.create(serviceId);
            // a 5xx answer is a provider failure and must count against the breaker; 4xx answers are the caller's fault
            builder.filter((request, next) -> breaker.run(next.exchange(request).flatMap(response ->
                    response.statusCode().is5xxServerError() ? response.createException().flatMap(Mono::error) : Mono.just(response))));
        }
        return builder.build();
    }
}
