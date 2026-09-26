package org.linlinjava.announce.orderservice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Same stance as announce-management in this phase: authentication happens at the gateway, which injects the
 * member as {@code X-User-Id} (staff as {@code X-Admin-Id}); this service accepts its routes without a login of
 * its own and disables CSRF for the stateless JSON API. Provider and carrier webhooks are protected by their
 * signatures instead. Not to be exposed directly to the internet until JWT validation replaces this.
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
