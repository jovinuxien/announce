package org.linlinjava.announce.gatewayadmin.machine;

import org.linlinjava.announce.gatewayadmin.security.IdentityForwardingFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * On every proxied request: drop the staff bearer token and the cookies (they are the edge's business, never the
 * backends') and add {@code X-Service-Token}. Runs after route resolution and before the routing filters.
 */
@Component
public class MachineTokenRelayFilter implements GlobalFilter, Ordered {

    public static final int ORDER = 10100;

    private final MachineTokens tokens;

    public MachineTokenRelayFilter(MachineTokens tokens) {
        this.tokens = tokens;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange mutated = exchange.mutate().request(r -> r.headers(h -> {
            h.remove(HttpHeaders.AUTHORIZATION);
            h.remove(HttpHeaders.COOKIE);
            h.set(IdentityForwardingFilter.SERVICE_TOKEN, tokens.token());
        })).build();
        return chain.filter(mutated);
    }
}
