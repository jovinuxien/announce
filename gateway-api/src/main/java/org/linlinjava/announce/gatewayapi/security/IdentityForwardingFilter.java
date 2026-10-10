package org.linlinjava.announce.gatewayapi.security;

import org.linlinjava.announce.gatewayapi.auth.AccessTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Runs before Spring Security: strips every client-supplied identity header (anti-spoofing): {@code X-User-*},
 * the staff headers {@code X-Staff-Id} and {@code X-Admin-Id} some backends read, the legacy {@code X-Seller-Id}
 * and {@code X-Service-Token}. It then verifies the Bearer token and, when valid, injects the trusted
 * {@code X-User-Id} and {@code X-User-Type=MEMBER} and puts a {@code ROLE_MEMBER} authentication into the reactive
 * security context. An invalid or absent token leaves the request anonymous; {@link SecurityConfig} then decides.
 * Nobody is staff on the customer edge, so no staff header ever leaves it.
 */
@Component
public class IdentityForwardingFilter implements WebFilter, Ordered {

    public static final String HDR_USER_ID = "X-User-Id";
    public static final String HDR_USER_TYPE = "X-User-Type";
    public static final String HDR_USER_PREFIX = "X-User-";
    /** Identity and trust headers outside {@code X-User-*} that only an edge may set (compared case-insensitively). */
    static final Set<String> STRIPPED = Set.of("x-seller-id", "x-staff-id", "x-admin-id", "x-service-token");
    public static final String USER_TYPE_MEMBER = AccessTokens.TYPE_MEMBER;
    /** Ahead of Spring Security's WebFilterChainProxy (-100) and of the WebSocket token filter's successor. */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    private static final Logger LOG = LoggerFactory.getLogger(IdentityForwardingFilter.class);

    private final JwtDecoder decoder;

    public IdentityForwardingFilter(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest.Builder builder = exchange.getRequest().mutate();
        builder.headers(h -> h.keySet().removeIf(IdentityForwardingFilter::clientMayNotSend));
        Optional<Jwt> jwt = bearer(exchange).flatMap(this::verify);
        jwt.ifPresent(j -> builder.headers(h -> {
            h.set(HDR_USER_ID, j.getSubject());
            h.set(HDR_USER_TYPE, USER_TYPE_MEMBER);
        }));
        Mono<Void> downstream = chain.filter(exchange.mutate().request(builder.build()).build());
        return jwt.map(IdentityForwardingFilter::authentication)
                .map(auth -> downstream.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .orElse(downstream);
    }

    static boolean clientMayNotSend(String header) {
        return header.regionMatches(true, 0, HDR_USER_PREFIX, 0, HDR_USER_PREFIX.length())
                || STRIPPED.contains(header.toLowerCase(java.util.Locale.ROOT));
    }

    private Optional<Jwt> verify(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            String type = jwt.getClaimAsString(AccessTokens.CLAIM_TYPE);
            if (!USER_TYPE_MEMBER.equals(type)) {
                LOG.debug("bearer token of type {} rejected on the customer edge", type);
                return Optional.empty();
            }
            return Optional.of(jwt);
        } catch (JwtException e) {
            LOG.debug("bearer token rejected: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static Authentication authentication(Jwt jwt) {
        return new UsernamePasswordAuthenticationToken(jwt.getSubject(), jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + SecurityConfig.ROLE_MEMBER)));
    }

    static Optional<String> bearer(ServerWebExchange exchange) {
        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = auth.substring(7).trim();
            return token.isEmpty() ? Optional.empty() : Optional.of(token);
        }
        return Optional.empty();
    }
}
