package org.linlinjava.announce.gatewayadmin.web;

import org.linlinjava.announce.gatewayadmin.security.PublicPaths;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * HTML5 history fallback: a GET whose last segment has no dot and that is not under a backend prefix
 * ({@link PublicPaths#BACKEND_PREFIXES}) is served the SPA shell ({@code /index.html}) so deep links such as
 * {@code /announces/abc} work. Runs before Spring Security so the rewritten request hits the public shell rule;
 * asset paths (with a dot) fall through to static handling and answer 404 when missing.
 */
@Component
public class SpaHistoryFallbackFilter implements WebFilter, Ordered {

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 50;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (HttpMethod.GET.equals(exchange.getRequest().getMethod()) && isSpaRoute(path)) {
            return chain.filter(exchange.mutate().request(exchange.getRequest().mutate().path("/index.html").build()).build());
        }
        return chain.filter(exchange);
    }

    static boolean isSpaRoute(String path) {
        if (PublicPaths.isBackend(path)) {
            return false;
        }
        String last = path.substring(path.lastIndexOf('/') + 1);
        return !last.contains(".");
    }
}
