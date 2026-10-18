package org.linlinjava.announce.cqrs.security;

import java.util.Locale;
import java.util.Set;

/**
 * Who is behind a request, as an edge forwarded it after authenticating the person: a member of the site
 * ({@code announce_user.id}, customer edge) or a staff member ({@code announce_admin.id}, admin edge). The two
 * are different number spaces, so the type always travels with the id.
 *
 * @param id    the member's or staff member's id
 * @param type  member or staff
 * @param roles the edge's authorities for staff: {@code ROLE_ADMIN}, {@code ROLE_SUPER} and the permission codes of
 *              the account's roles ({@code admin:announce:moderate}, ...); empty for members
 */
public record Caller(Integer id, Type type, Set<String> roles) {

    public enum Type {
        /** A member, forwarded by the customer edge as {@code X-User-Type=MEMBER}. */
        MEMBER,
        /** A staff member, forwarded by the admin edge as {@code X-User-Type=ADMIN}. */
        ADMIN
    }

    public Caller {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean staff() {
        return type == Type.ADMIN;
    }

    public boolean member() {
        return type == Type.MEMBER;
    }

    /** The authority that grants every permission (the super administrator). */
    public static final String ROLE_SUPER = "ROLE_SUPER";
    /** The permission code that grants every permission; the admin edge expands it but also forwards it as is. */
    public static final String ALL_PERMISSIONS = "*";

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    /**
     * Whether the caller holds a permission: true for {@code *}, for {@code ROLE_SUPER} and for the code itself
     * (codes compare lower-cased, as {@link Callers} stores them). Members hold no permission. The framework knows
     * no catalogue of codes: the string is whatever the admin edge's catalogue and the backend route agree on.
     */
    public boolean can(String permission) {
        if (permission == null || permission.isBlank()) {
            return false;
        }
        return roles.contains(ALL_PERMISSIONS) || roles.contains(ROLE_SUPER)
                || roles.contains(permission.trim().toLowerCase(Locale.ROOT));
    }
}
