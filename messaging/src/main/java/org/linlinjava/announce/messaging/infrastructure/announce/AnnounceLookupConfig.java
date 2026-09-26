package org.linlinjava.announce.messaging.infrastructure.announce;

import org.linlinjava.announce.cqrs.api.HttpExchangeClients;
import org.linlinjava.announce.messaging.application.AnnounceLookup;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * {@link AnnounceLookup} over the announce-management API through the framework's discovered, circuit-broken
 * client ({@code cqrs.api.clients.announce-management.base-url} pins the URL outside the registry). Tests replace
 * it with {@code messaging.announce-lookup=stub} and their own bean.
 */
@Configuration(proxyBeanMethods = false)
public class AnnounceLookupConfig {

    public static final String SERVICE_ID = "announce-management";

    @Bean
    @ConditionalOnProperty(name = "messaging.announce-lookup", havingValue = "api", matchIfMissing = true)
    AnnounceLookup announceApiLookup(HttpExchangeClients clients) {
        AnnounceApi api = clients.create(AnnounceApi.class, SERVICE_ID);
        return announceId -> api.get(announceId).onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty());
    }
}
