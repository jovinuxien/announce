package org.linlinjava.announce.gatewayadmin.security;

import org.linlinjava.announce.gatewayadmin.jwt.AdminJwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;

/**
 * Turns a bearer token into an {@link AdminAuthentication}. A token that verifies but is not of type {@code ADMIN}
 * (a member token, should the edges ever share a key) is authenticated with its own roles only, so the
 * authorisation rules answer 403 rather than 401.
 */
public class JwtAuthenticationManager implements ReactiveAuthenticationManager {

    private final AdminJwtService jwt;

    public JwtAuthenticationManager(AdminJwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = String.valueOf(authentication.getCredentials());
        return Mono.justOrEmpty(jwt.verify(token))
                .<Authentication>map(v -> new AdminAuthentication(v.principal(), v.type()))
                .switchIfEmpty(Mono.error(new BadCredentialsException("invalid or expired token")));
    }
}
