package org.linlinjava.announce.cqrs.security;

import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the {@link Caller} from the identity headers an edge injects: {@code X-User-Id}, {@code X-User-Type}
 * ({@code MEMBER} from the customer edge, {@code ADMIN} from the admin edge) and {@code X-User-Roles}. The
 * headers can be trusted because {@link ServiceTokenWebFilter} refuses every request that did not pass an edge,
 * and both edges strip whatever a client sent under these names.
 *
 * <p>Every service reads its callers through this class so the convention is one: a member route calls
 * {@link #member}, a staff route {@link #staff}, a route both may use {@link #any}; a public route that behaves
 * differently for a signed-in member calls {@link #memberIfAny}. Failures are problem details through
 * {@link CommandRejectedException}: 401 {@code no-principal} without an identity, 403 {@code not-a-member} /
 * {@code not-staff} for the wrong kind of caller, 403 {@code unknown-caller-type} for a type that is neither, 403
 * {@code missing-permission} for a staff member whose roles lack the permission a route names
 * ({@link #staff(HttpHeaders, String)}).
 *
 * <p>{@code X-User-Roles} is comma separated. Authorities ({@code ROLE_...}) are kept upper-cased; every other
 * value is a permission code and is kept lower-cased, so {@link Caller#can} compares case-insensitively.
 */
public final class Callers {

    public static final String USER_HEADER = "X-User-Id";
    public static final String TYPE_HEADER = "X-User-Type";
    public static final String ROLES_HEADER = "X-User-Roles";
    public static final String MEMBER = "MEMBER";
    public static final String ADMIN = "ADMIN";

    public static final String NO_PRINCIPAL = "no-principal";
    public static final String NOT_A_MEMBER = "not-a-member";
    public static final String NOT_STAFF = "not-staff";
    public static final String UNKNOWN_TYPE = "unknown-caller-type";
    public static final String MISSING_PERMISSION = "missing-permission";

    private Callers() {
    }

    /** The caller, or empty for an anonymous request. 400 for an id that is not a number, 403 for an unknown type. */
    public static Optional<Caller> caller(HttpHeaders headers) {
        String raw = headers.getFirst(USER_HEADER);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        Integer id;
        try {
            id = Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(USER_HEADER + " must be a number");
        }
        String type = headers.getFirst(TYPE_HEADER);
        Caller.Type resolved = MEMBER.equals(type) ? Caller.Type.MEMBER : ADMIN.equals(type) ? Caller.Type.ADMIN : null;
        if (resolved == null) {
            throw new CommandRejectedException(HttpStatus.FORBIDDEN, UNKNOWN_TYPE,
                    TYPE_HEADER + " must be " + MEMBER + " or " + ADMIN);
        }
        return Optional.of(new Caller(id, resolved, roles(headers.getFirst(ROLES_HEADER))));
    }

    /** A signed-in member or staff member: 401 for an anonymous request. */
    public static Caller any(HttpHeaders headers) {
        return caller(headers).orElseThrow(() -> new CommandRejectedException(HttpStatus.UNAUTHORIZED, NO_PRINCIPAL,
                "Sign in (" + USER_HEADER + ")"));
    }

    /** The member behind a member route: 401 without an identity, 403 for staff. */
    public static Integer member(HttpHeaders headers) {
        Caller caller = any(headers);
        if (!caller.member()) {
            throw new CommandRejectedException(HttpStatus.FORBIDDEN, NOT_A_MEMBER, "Only a member can do this");
        }
        return caller.id();
    }

    /** The staff member behind an admin route: 401 without an identity, 403 for a member. */
    public static Integer staff(HttpHeaders headers) {
        Caller caller = any(headers);
        if (!caller.staff()) {
            throw new CommandRejectedException(HttpStatus.FORBIDDEN, NOT_STAFF, "Only staff can do this");
        }
        return caller.id();
    }

    /**
     * The staff member behind an admin route that needs a permission: the checks of {@link #staff(HttpHeaders)}
     * first, then 403 {@code missing-permission} (the code in the detail) unless {@link Caller#can} holds.
     */
    public static Integer staff(HttpHeaders headers, String permission) {
        Caller caller = any(headers);
        if (!caller.staff()) {
            throw new CommandRejectedException(HttpStatus.FORBIDDEN, NOT_STAFF, "Only staff can do this");
        }
        if (!caller.can(permission)) {
            throw new CommandRejectedException(HttpStatus.FORBIDDEN, MISSING_PERMISSION,
                    "This action needs the permission " + permission);
        }
        return caller.id();
    }

    /** The member looking at a public page, or empty for a visitor and for staff. */
    public static Optional<Integer> memberIfAny(HttpHeaders headers) {
        return caller(headers).filter(Caller::member).map(Caller::id);
    }

    static Set<String> roles(String header) {
        if (header == null || header.isBlank()) {
            return Set.of();
        }
        Set<String> roles = new LinkedHashSet<>();
        Arrays.stream(header.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Callers::normalise).forEach(roles::add);
        return roles;
    }

    /** {@code ROLE_...} authorities upper-cased, permission codes lower-cased. */
    static String normalise(String value) {
        return value.regionMatches(true, 0, "ROLE_", 0, 5) ? value.toUpperCase(Locale.ROOT) : value.toLowerCase(Locale.ROOT);
    }
}
