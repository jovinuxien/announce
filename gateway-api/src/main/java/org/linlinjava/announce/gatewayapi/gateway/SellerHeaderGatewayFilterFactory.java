package org.linlinjava.announce.gatewayapi.gateway;

import org.linlinjava.announce.gatewayapi.security.IdentityForwardingFilter;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

/**
 * Route filter {@code SellerHeader}: copies the trusted {@code X-User-Id} into {@code X-Seller-Id}, the owner header
 * announce-management reads today. Used only on that module's routes; dropped once it reads {@code X-User-Id}.
 * The client's own {@code X-Seller-Id} was already stripped by {@link IdentityForwardingFilter}.
 */
@Component
public class SellerHeaderGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    public SellerHeaderGatewayFilterFactory() {
        super(Object.class);
    }

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> {
            String userId = exchange.getRequest().getHeaders().getFirst(IdentityForwardingFilter.HDR_USER_ID);
            if (userId == null) return chain.filter(exchange);
            return chain.filter(exchange.mutate()
                    .request(r -> r.headers(h -> h.set(IdentityForwardingFilter.HDR_SELLER_ID, userId)))
                    .build());
        };
    }

    @Override
    public String name() {
        return "SellerHeader";
    }
}
