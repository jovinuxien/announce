package org.linlinjava.announce.gatewayapi.machine;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Clock;

/**
 * Adds {@code X-Service-Token} on every route whose metadata says {@code serviceToken: true} (the {@code lb://}
 * routes to our own services; the OCS routes do not carry it) so backends can later refuse traffic that did not
 * pass an edge. Order 10100: after route resolution, before the load balancer and the Netty routing filter.
 */
@Component
public class MachineTokenRelayFilter implements GlobalFilter, Ordered {

    public static final String METADATA_KEY = "serviceToken";
    public static final int ORDER = 10100;

    private final MachineTokenProperties props;
    private final Clock clock;

    public MachineTokenRelayFilter(MachineTokenProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null || !wantsToken(route) || props.secret() == null || props.secret().isBlank()) {
            return chain.filter(exchange);
        }
        String token = MachineTokens.mint(props, clock.instant().getEpochSecond());
        return chain.filter(exchange.mutate()
                .request(r -> r.headers(h -> h.set(MachineTokens.HEADER, token)))
                .build());
    }

    private static boolean wantsToken(Route route) {
        Object flag = route.getMetadata().get(METADATA_KEY);
        return flag != null && Boolean.parseBoolean(String.valueOf(flag));
    }
}
