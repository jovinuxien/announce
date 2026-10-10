package org.linlinjava.announce.orderservice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Same stance as announce-management: authentication happens at the edges, which inject the member as
 * {@code X-User-Id} (staff as {@code X-Admin-Id}) and sign every proxied request with {@code X-Service-Token}.
 * The framework's {@code ServiceTokenWebFilter} ({@code announce.service-token.mode=enforce}) answers 401 to a
 * request without a valid token before this chain runs, so this service has no login of its own and disables CSRF
 * for the stateless JSON API. Provider and carrier webhooks arrive through the customer edge too and are
 * additionally protected by their signatures.
 */
@Configuration
@EnableWebFluxSecurity
public class OrderSecurityConfig {

    @Bean
    public SecurityWebFilterChain orderSecurity(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(ex -> ex.anyExchange().permitAll())
                .build();
    }
}
