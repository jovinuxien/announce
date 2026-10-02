package org.linlinjava.announce.goods.infrastructure.configuration.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Security of the catalog API: authentication happens at the edges ({@code gateway-api} for members,
 * {@code gateway-admin} for staff), which inject the caller as {@code X-User-Id} / {@code X-User-Type} and sign
 * every proxied request with {@code X-Service-Token}. The framework's {@code ServiceTokenWebFilter}
 * ({@code announce.service-token.mode=enforce}) answers 401 to a request without a valid token before this chain
 * runs, so the identity headers can be trusted. This chain therefore has no login of its own and disables CSRF
 * for the stateless JSON API.
 */
@Configuration
@EnableWebFluxSecurity
public class CatalogSecurityConfig {

    @Bean
    public SecurityWebFilterChain catalogSecurity(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(ex -> ex
                        .pathMatchers("/api/**", "/management/**", "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        .anyExchange().permitAll())
                .build();
    }
}
