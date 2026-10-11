package org.linlinjava.announce.gatewayadmin.security;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * Browsers cannot set headers on a WebSocket handshake, so the admin SPA passes the staff access token as the
 * {@code access_token} query parameter on {@code /ws/**} ({@code /ws/staff} for bulletins, {@code /ws/conversations/{id}}
 * for a support chat). This filter turns it into the Authorization header (and drops it from the forwarded URI)
 * before {@link IdentityForwardingFilter} verifies it. Same contract as the customer edge.
 */
@Component
public class WebSocketTokenFilter implements WebFilter, Ordered {

    public static final String PARAM = "access_token";
    public static final String PREFIX = "/ws/";

    @Override
    public int getOrder() {
        return IdentityForwardingFilter.ORDER - 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String token = request.getQueryParams().getFirst(PARAM);
        if (token == null || token.isBlank() || !request.getURI().getRawPath().startsWith(PREFIX)
                || request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            return chain.filter(exchange);
        }
        URI stripped = UriComponentsBuilder.fromUri(request.getURI()).replaceQueryParam(PARAM).build(true).toUri();
        ServerHttpRequest mutated = request.mutate()
                .uri(stripped)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }
}
