package org.linlinjava.announce.gatewayadmin.security;

import org.linlinjava.announce.gatewayadmin.jwt.AdminJwtService;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Anti-spoofing plus identity forwarding. Every request loses any client-supplied {@code X-User-*},
 * {@code X-Seller-Id}, the former staff headers {@code X-Staff-Id} / {@code X-Admin-Id} and {@code X-Service-Token};
 * when the bearer token is a valid staff token the edge injects {@code X-User-Id}, {@code X-User-Type=ADMIN} and
 * {@code X-User-Roles}, which is how every backend identifies staff (the framework's {@code Callers}). Runs before Spring
 * Security, so the header set is fixed whatever happens later in the chain.
 */
@Component
public class IdentityForwardingFilter implements WebFilter, Ordered {

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 100;
    public static final String USER_ID = "X-User-Id";
    public static final String USER_TYPE = "X-User-Type";
    public static final String USER_ROLES = "X-User-Roles";
    public static final String SELLER_ID = "X-Seller-Id";
    public static final String SERVICE_TOKEN = "X-Service-Token";
    static final String[] STRIPPED = {USER_ID, USER_TYPE, USER_ROLES, SELLER_ID, "X-Staff-Id", "X-Admin-Id", SERVICE_TOKEN};

    private final AdminJwtService jwt;

    public IdentityForwardingFilter(AdminJwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest.Builder builder = exchange.getRequest().mutate().headers(h -> {
            for (String name : STRIPPED) {
                h.remove(name);
            }
        });
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            jwt.verify(header.substring(7).trim()).filter(AdminJwtService.Verified::isAdmin).ifPresent(v -> builder.headers(h -> {
                h.set(USER_ID, String.valueOf(v.principal().id()));
                h.set(USER_TYPE, AdminJwtService.TYPE_ADMIN);
                h.set(USER_ROLES, String.join(",", v.principal().authorities()));
            }));
        }
        return chain.filter(exchange.mutate().request(builder.build()).build());
    }
}
