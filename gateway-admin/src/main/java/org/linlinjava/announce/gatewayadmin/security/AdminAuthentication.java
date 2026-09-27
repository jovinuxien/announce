package org.linlinjava.announce.gatewayadmin.security;

import org.linlinjava.announce.gatewayadmin.auth.AdminPrincipal;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** A verified bearer token as a Spring Security authentication; the principal is the {@link AdminPrincipal}. */
public class AdminAuthentication extends AbstractAuthenticationToken {

    private final AdminPrincipal principal;
    private final String type;

    public AdminAuthentication(AdminPrincipal principal, String type) {
        super(principal.authorities().stream().map(SimpleGrantedAuthority::new).toList());
        this.principal = principal;
        this.type = type;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public AdminPrincipal getPrincipal() {
        return principal;
    }

    /** {@code ADMIN} for staff tokens; anything else is a token of another edge signed with the same key. */
    public String type() {
        return type;
    }
}
