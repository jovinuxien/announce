package org.linlinjava.announce.cqrs.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Refuses HTTP (and WebSocket handshake) requests that do not carry a fresh, correctly signed
 * {@code X-Service-Token}: the identity headers of such a request were not set by an edge and must not be
 * trusted. Open paths (health checks) pass without a token. In the {@code log} mode a failure is only logged.
 *
 * <p>The verified caller is exposed as the exchange attribute {@link #CALLER_ATTRIBUTE}.
 */
public class ServiceTokenWebFilter implements WebFilter {

    /** Exchange attribute holding the name of the service that minted the verified token. */
    public static final String CALLER_ATTRIBUTE = ServiceTokenWebFilter.class.getName() + ".caller";
    public static final String PROBLEM_TYPE_MISSING = "urn:announce:cqrs:service-token-required";
    public static final String PROBLEM_TYPE_INVALID = "urn:announce:cqrs:service-token-invalid";

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenWebFilter.class);

    private final ServiceTokenProperties.Mode mode;
    private final String secret;
    private final long maxSkewSeconds;
    private final List<PathPattern> openPaths;
    private final Clock clock;

    public ServiceTokenWebFilter(ServiceTokenProperties properties, Clock clock) {
        this.mode = properties.resolvedMode();
        this.secret = properties.getSecret();
        this.maxSkewSeconds = properties.getMaxSkew().toSeconds();
        this.openPaths = properties.getOpenPaths().stream().map(PathPatternParser.defaultInstance::parse).toList();
        this.clock = clock;
        if (mode != ServiceTokenProperties.Mode.OFF && (secret == null || secret.isBlank())) {
            throw new IllegalStateException("announce.service-token.mode=" + properties.getMode()
                    + " needs announce.service-token.secret (ANNOUNCE_SERVICE_SECRET)");
        }
    }

    /** After the correlation filter (so a refusal still carries the correlation id), before everything else. */
    public static int order() {
        return Integer.MIN_VALUE + 200;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (mode == ServiceTokenProperties.Mode.OFF || isOpen(exchange)) {
            return chain.filter(exchange);
        }
        String token = exchange.getRequest().getHeaders().getFirst(ServiceTokens.HEADER);
        Optional<String> caller = ServiceTokens.verify(secret, token, clock.instant().getEpochSecond(), maxSkewSeconds);
        if (caller.isPresent()) {
            exchange.getAttributes().put(CALLER_ATTRIBUTE, caller.get());
            return chain.filter(exchange);
        }
        boolean missing = token == null || token.isBlank();
        if (mode == ServiceTokenProperties.Mode.LOG) {
            log.warn("{} {} served although its {} is {} (announce.service-token.mode=log)", exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath(), ServiceTokens.HEADER, missing ? "missing" : "invalid or stale");
            return chain.filter(exchange);
        }
        log.debug("{} {} refused: {} {}", exchange.getRequest().getMethod(), exchange.getRequest().getPath(),
                ServiceTokens.HEADER, missing ? "missing" : "invalid or stale");
        return refuse(exchange.getResponse(), missing);
    }

    private boolean isOpen(ServerWebExchange exchange) {
        var path = exchange.getRequest().getPath().pathWithinApplication();
        return openPaths.stream().anyMatch(pattern -> pattern.matches(path));
    }

    private static Mono<Void> refuse(ServerHttpResponse response, boolean missing) {
        String body = "{\"type\":\"" + (missing ? PROBLEM_TYPE_MISSING : PROBLEM_TYPE_INVALID) + "\","
                + "\"title\":\"Unauthorized\",\"status\":401,\"detail\":\""
                + (missing ? "This service only answers requests that passed an edge" : "The service token is invalid or stale")
                + "\"}";
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
