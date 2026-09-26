package org.linlinjava.announce.gatewayadmin.web.admin;

import java.util.List;

/**
 * The permission strings a role may hold. Only {@code *} is enforced today (it grants {@code ROLE_SUPER}); the
 * module codes are stored so the SPA can show what a role is for and a later step can enforce them per route.
 */
public final class PermissionCatalogue {

    public record Permission(String code, String description) {}

    public static final List<Permission> ALL = List.of(
            new Permission("*", "Everything: super administrator (manage administrators, roles and permissions)"),
            new Permission("admin:announce:moderate", "Announces: list every announce, withdraw and expire with a reason"),
            new Permission("admin:catalog:manage", "Categories per kind and brands"),
            new Permission("admin:listing:manage", "Crawled listings: import, reindex, statistics"),
            new Permission("admin:member:manage", "Members: list, search, enable or disable"),
            new Permission("admin:region:manage", "Regions"),
            new Permission("admin:order:manage", "Auctions, orders, disputes and payouts"),
            new Permission("admin:support:manage", "Support queue and bulletins"),
            new Permission("admin:dashboard:read", "Dashboard counts"));

    private PermissionCatalogue() {}

    public static boolean isKnown(String code) {
        return ALL.stream().anyMatch(p -> p.code().equals(code));
    }
}
