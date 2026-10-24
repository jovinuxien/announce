package org.linlinjava.announce.messaging.infrastructure.announce;

import org.linlinjava.announce.cqrs.api.HttpExchangeClients;
import org.linlinjava.announce.goods.api.AnnounceApi;
import org.linlinjava.announce.messaging.application.AnnounceLookup;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * {@link AnnounceLookup} over the descriptor announce-management publishes in {@code announce-management-api}
 * ({@link AnnounceApi}), through the framework's discovered, circuit-broken client
 * ({@code cqrs.api.clients.announce-management.base-url} pins the URL outside the registry,
 * {@code .service-token=true} signs the calls). The call carries no member identity, so only PUBLISHED announces
 * are found, which is what a listing conversation needs. Tests replace the bean with
 * {@code messaging.announce-lookup=stub} and their own stub.
 */
@Configuration(proxyBeanMethods = false)
public class AnnounceLookupConfig {

    public static final String SERVICE_ID = "announce-management";

    @Bean
    @ConditionalOnProperty(name = "messaging.announce-lookup", havingValue = "api", matchIfMissing = true)
    AnnounceLookup announceApiLookup(HttpExchangeClients clients) {
        AnnounceApi api = clients.create(AnnounceApi.class, SERVICE_ID);
        return announceId -> api.view(announceId)
                .map(a -> new AnnounceLookup.AnnounceFacts(a.id(), a.sellerId(), a.title(), a.status() == null ? null : a.status().name()))
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty());
    }
}
