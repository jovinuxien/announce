package org.linlinjava.announce.gatewayadmin.auth;

import org.springframework.security.core.AuthenticatedPrincipal;

import java.util.List;
import java.util.Set;

/**
 * The authenticated administrator as carried in the token: account id, username, avatar, role ids and the
 * authorities derived from the roles ({@code ROLE_ADMIN} for any enabled role, {@code ROLE_SUPER} when one of the
 * roles holds the {@code *} permission).
 */
public record AdminPrincipal(Integer id, String username, String avatar, List<Integer> roleIds, Set<String> authorities) implements AuthenticatedPrincipal {

    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_SUPER = "ROLE_SUPER";

    @Override
    public String getName() {
        return username;
    }

    public boolean isSuper() {
        return authorities.contains(ROLE_SUPER);
    }
}
