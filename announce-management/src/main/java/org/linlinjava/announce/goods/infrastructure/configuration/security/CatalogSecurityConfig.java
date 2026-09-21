package org.linlinjava.announce.goods.infrastructure.configuration.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Security of the catalog API in this phase: authentication and authorisation happen at the gateway, which is
 * expected to inject the member as the {@code X-Seller-Id} header. This service therefore accepts the API and
 * actuator routes without its own login, and disables CSRF for the stateless JSON API.
 *
 * <p>Replace with resource-server JWT validation once the auth-server issues tokens; until then this service must
 * not be exposed directly to the internet.
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
