package org.linlinjava.announce.gatewayapi.security;

import org.linlinjava.announce.gatewayapi.web.ProblemDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * Deny by default: every exchange needs a member ({@code ROLE_MEMBER}, set by {@link IdentityForwardingFilter})
 * unless {@link PublicPaths} lists it. Stateless: no session, no CSRF, problem-detail bodies for 401 and 403.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    public static final String ROLE_MEMBER = "MEMBER";

    @Bean
    public SecurityWebFilterChain edgeSecurity(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((exchange, e) ->
                                ProblemDetails.write(exchange, HttpStatus.UNAUTHORIZED, "a valid bearer token is required"))
                        .accessDeniedHandler((exchange, e) ->
                                ProblemDetails.write(exchange, HttpStatus.FORBIDDEN, "not allowed for this member")))
                .authorizeExchange(ex -> ex
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .matchers(PublicPaths.matcher()).permitAll()
                        .anyExchange().hasRole(ROLE_MEMBER))
                .build();
    }
}
