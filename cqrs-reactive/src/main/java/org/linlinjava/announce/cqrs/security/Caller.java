package org.linlinjava.announce.cqrs.security;

import java.util.Set;

/**
 * Who is behind a request, as an edge forwarded it after authenticating the person: a member of the site
 * ({@code announce_user.id}, customer edge) or a staff member ({@code announce_admin.id}, admin edge). The two
 * are different number spaces, so the type always travels with the id.
 *
 * @param id    the member's or staff member's id
 * @param type  member or staff
 * @param roles the edge's authorities for staff ({@code ROLE_ADMIN}, {@code ROLE_SUPER}, ...); empty for members
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

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
