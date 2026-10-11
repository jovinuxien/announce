package org.linlinjava.announce.gatewayadmin.security;

import org.linlinjava.announce.gatewayadmin.auth.AdminPrincipal;
import org.linlinjava.announce.gatewayadmin.jwt.AdminJwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;
import reactor.core.publisher.Mono;

/**
 * Deny by default. Public: {@link PublicPaths}. {@code /api/**} and the WebSocket handshakes under {@code /ws/**}
 * (token from the {@code access_token} query parameter, {@link WebSocketTokenFilter}) need {@code ROLE_ADMIN};
 * administrators, roles and permissions need {@code ROLE_SUPER}; {@code /auth/me} needs any valid staff token. Stateless: no session, no CSRF
 * (JSON with a bearer token), the refresh cookie is only read by {@code /auth/refresh} and {@code /auth/logout}.
 * The edge's own 401 / 403 are RFC 7807 problems like the backends'.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
public class SecurityConfig {

    public static final String[] SUPER_PATHS = {"/api/admin/admins/**", "/api/admin/roles/**", "/api/admin/permissions/**"};

    public static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data: https:; font-src 'self' data:; connect-src 'self'; frame-ancestors 'none'";

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, AdminJwtService jwt) {
        AuthenticationWebFilter bearer = new AuthenticationWebFilter(new JwtAuthenticationManager(jwt));
        bearer.setServerAuthenticationConverter(bearerConverter());
        bearer.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());
        bearer.setAuthenticationFailureHandler((exchange, e) ->
                ProblemResponses.write(exchange.getExchange(), HttpStatus.UNAUTHORIZED, "invalid-token", e.getMessage()));

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives(CSP))
                        .frameOptions(f -> f.mode(XFrameOptionsServerHttpHeadersWriter.Mode.DENY))
                        .referrerPolicy(r -> r.policy(org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .addFilterAt(bearer, SecurityWebFiltersOrder.AUTHENTICATION)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((exchange, ex) -> ProblemResponses.write(exchange, HttpStatus.UNAUTHORIZED, "unauthenticated", "A staff bearer token is required"))
                        .accessDeniedHandler((exchange, ex) -> ProblemResponses.write(exchange, HttpStatus.FORBIDDEN, "forbidden", "This action needs a role you do not have")))
                .authorizeExchange(ex -> ex
                        .pathMatchers(PublicPaths.ALL).permitAll()
                        .pathMatchers("/auth/me").hasAuthority(AdminPrincipal.ROLE_ADMIN)
                        .pathMatchers(SUPER_PATHS).hasAuthority(AdminPrincipal.ROLE_SUPER)
                        .pathMatchers("/api/**", "/ws/**").hasAuthority(AdminPrincipal.ROLE_ADMIN)
                        .anyExchange().denyAll())
                .build();
    }

    /** {@code Authorization: Bearer <token>} to an unauthenticated token holding the raw string as credentials. */
    static ServerAuthenticationConverter bearerConverter() {
        return exchange -> {
            String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (header == null || header.length() < 8 || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return Mono.empty();
            }
            String token = header.substring(7).trim();
            return Mono.<Authentication>just(new UsernamePasswordAuthenticationToken(token, token));
        };
    }
}
