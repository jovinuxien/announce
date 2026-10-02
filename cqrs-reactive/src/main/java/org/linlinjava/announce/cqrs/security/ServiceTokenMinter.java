package org.linlinjava.announce.cqrs.security;

import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

import java.time.Clock;

/**
 * Mints {@code X-Service-Token} for this service's own calls to another of our services (which then accepts the
 * call like one coming from an edge). Used by {@code HttpExchangeClients} for the clients that opt in with
 * {@code cqrs.api.clients.<serviceId>.service-token=true}; never attach it to a call leaving our own services.
 */
public class ServiceTokenMinter {

    private final String service;
    private final String secret;
    private final Clock clock;

    public ServiceTokenMinter(String service, String secret, Clock clock) {
        this.service = service;
        this.secret = secret;
        this.clock = clock;
    }

    /** Whether a secret is configured; minting fails without one. */
    public boolean configured() {
        return secret != null && !secret.isBlank();
    }

    public String token() {
        if (!configured()) {
            throw new IllegalStateException("announce.service-token.secret (ANNOUNCE_SERVICE_SECRET) is needed to call a service with a service token");
        }
        return ServiceTokens.mint(service, secret, clock.instant().getEpochSecond());
    }

    /** A {@code WebClient} filter that sets a freshly minted token on every request. */
    public ExchangeFilterFunction exchangeFilter() {
        return (request, next) -> next.exchange(ClientRequest.from(request)
                .headers(headers -> headers.set(ServiceTokens.HEADER, token())).build());
    }
}
