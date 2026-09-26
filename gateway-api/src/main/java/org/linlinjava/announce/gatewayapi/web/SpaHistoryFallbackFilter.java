package org.linlinjava.announce.gatewayapi.web;

import org.linlinjava.announce.gatewayapi.security.PublicPaths;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * History-API fallback for the SPA: a GET or HEAD whose path is not under an API prefix and whose last segment has
 * no dot (so {@code /announce/abc} but not {@code /app/main.js}) is rewritten to {@code /index.html}, which the
 * static resource handler serves from {@code classpath:/static}. Assets that do not exist stay 404.
 * Runs after Spring Security (order 10), so the security decision applies to the original path.
 */
@Component
public class SpaHistoryFallbackFilter implements WebFilter, Ordered {

    public static final String INDEX = "/index.html";
    public static final int ORDER = 10;

    private final List<PathPattern> apiPrefixes = Arrays.stream(PublicPaths.API_PREFIXES)
            .map(PathPatternParser.defaultInstance::parse).toList();

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (!isSpaNavigation(request)) return chain.filter(exchange);
        return chain.filter(exchange.mutate().request(request.mutate().path(INDEX).build()).build());
    }

    boolean isSpaNavigation(ServerHttpRequest request) {
        HttpMethod method = request.getMethod();
        if (method != HttpMethod.GET && method != HttpMethod.HEAD) return false;
        String path = request.getURI().getRawPath();
        if (path.isEmpty() || "/".equals(path)) return true;
        if (apiPrefixes.stream().anyMatch(p -> p.matches(request.getPath().pathWithinApplication()))) return false;
        String last = path.substring(path.lastIndexOf('/') + 1);
        if (last.contains(".")) return false;
        return acceptsHtml(request);
    }

    private static boolean acceptsHtml(ServerHttpRequest request) {
        try {
            List<MediaType> accept = request.getHeaders().getAccept();
            return accept.isEmpty() || accept.stream().anyMatch(m -> m.includes(MediaType.TEXT_HTML));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
