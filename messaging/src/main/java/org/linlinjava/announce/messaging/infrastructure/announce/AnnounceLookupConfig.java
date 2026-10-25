package org.linlinjava.announce.messaging.infrastructure.announce;

import org.linlinjava.announce.cqrs.api.HttpExchangeClients;
import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.goods.api.AnnounceApi;
import org.linlinjava.announce.messaging.application.AnnounceLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * {@link AnnounceLookup} over the descriptor announce-management publishes in {@code announce-management-api}
 * ({@link AnnounceApi}), through the framework's discovered, circuit-broken client
 * ({@code cqrs.api.clients.announce-management.base-url} pins the URL outside the registry,
 * {@code .service-token=true} signs the calls). The call carries no member identity, so only PUBLISHED announces
 * are found, which is what a listing conversation needs. Tests replace the bean with
 * {@code messaging.announce-lookup=stub} and their own stub.
 *
 * <p>Error policy: a 4xx answer about the id (404 unknown, 400 or 422 malformed) means "no such announce" and
 * completes empty, so the caller answers its own 404 {@code announce-not-found}. A 401 or 403 means the service
 * token of this instance is refused, a 5xx or a transport failure means announce-management is not answering:
 * those are not the member's fault and surface as 503 {@value #LOOKUP_FAILED} instead of a bare 500.
 */
@Configuration(proxyBeanMethods = false)
public class AnnounceLookupConfig {

    public static final String SERVICE_ID = "announce-management";
    public static final String LOOKUP_FAILED = "announce-lookup-failed";

    private static final Logger log = LoggerFactory.getLogger(AnnounceLookupConfig.class);

    @Bean
    @ConditionalOnProperty(name = "messaging.announce-lookup", havingValue = "api", matchIfMissing = true)
    AnnounceLookup announceApiLookup(HttpExchangeClients clients) {
        return over(clients.create(AnnounceApi.class, SERVICE_ID));
    }

    /** The lookup over a descriptor client; package-visible so a test can drive it with a stubbed {@link AnnounceApi}. */
    static AnnounceLookup over(AnnounceApi api) {
        return announceId -> api.view(announceId)
                .map(a -> new AnnounceLookup.AnnounceFacts(a.id(), a.sellerId(), a.title(), a.status() == null ? null : a.status().name()))
                .onErrorResume(e -> !(e instanceof CommandRejectedException), e -> translate(announceId, e));
    }

    private static Mono<AnnounceLookup.AnnounceFacts> translate(String announceId, Throwable e) {
        if (e instanceof WebClientResponseException http) {
            HttpStatus status = HttpStatus.resolve(http.getStatusCode().value());
            boolean authFailure = status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN;
            if (http.getStatusCode().is4xxClientError() && !authFailure) {
                log.debug("announce-management has no announce {} ({})", announceId, http.getStatusCode());
                return Mono.empty();
            }
        }
        log.warn("announce-management could not answer the lookup of announce {}: {}", announceId, e.toString());
        return Mono.error(new CommandRejectedException(HttpStatus.SERVICE_UNAVAILABLE, LOOKUP_FAILED,
                "The announce service did not answer the lookup of announce " + announceId));
    }
}
