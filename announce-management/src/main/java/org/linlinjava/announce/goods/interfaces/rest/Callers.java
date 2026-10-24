package org.linlinjava.announce.goods.interfaces.rest;

import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.cqrs.security.Caller;
import org.linlinjava.announce.goods.domain.announce.SellerId;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

/**
 * This context's view of the caller: the framework's {@link org.linlinjava.announce.cqrs.security.Callers} reads
 * {@code X-User-Id} and {@code X-User-Type} ({@code MEMBER} from the customer edge, {@code ADMIN} from the admin
 * edge); a member is the {@link SellerId} of the announces they own. The headers are trustworthy because the
 * framework's {@code ServiceTokenWebFilter} refuses every request that did not pass an edge.
 *
 * <p>Two ways to ask. The {@link HttpHeaders} overloads read the headers the route received. The no-argument
 * overloads read the {@link Resolution} that {@link CallerContextFilter} put into the Reactor context, for routes
 * that implement an {@code @HttpExchange} descriptor and therefore carry no header parameter; they answer the same
 * problem details (401 {@code no-principal}, 403 {@code not-a-member} / {@code not-staff} / {@code missing-permission},
 * 400 for an id that is not a number) and fail with {@link IllegalStateException} when the filter is not installed.
 */
public final class Callers {

    public static final String USER_HEADER = org.linlinjava.announce.cqrs.security.Callers.USER_HEADER;
    public static final String TYPE_HEADER = org.linlinjava.announce.cqrs.security.Callers.TYPE_HEADER;
    public static final String MEMBER = org.linlinjava.announce.cqrs.security.Callers.MEMBER;
    public static final String ADMIN = org.linlinjava.announce.cqrs.security.Callers.ADMIN;
    public static final String NO_PRINCIPAL = org.linlinjava.announce.cqrs.security.Callers.NO_PRINCIPAL;
    public static final String NOT_A_MEMBER = org.linlinjava.announce.cqrs.security.Callers.NOT_A_MEMBER;
    public static final String NOT_STAFF = org.linlinjava.announce.cqrs.security.Callers.NOT_STAFF;
    public static final String MISSING_PERMISSION = org.linlinjava.announce.cqrs.security.Callers.MISSING_PERMISSION;

    /** Reactor context key under which {@link CallerContextFilter} stores the request's {@link Resolution}. */
    public static final String CONTEXT_KEY = Callers.class.getName() + ".resolution";

    private Callers() {
    }

    // ---- from the headers of the route -------------------------------------------------------------------

    /** The member behind a write: 401 without an identity, 403 when the caller is not a member. */
    public static SellerId member(HttpHeaders headers) {
        return new SellerId(org.linlinjava.announce.cqrs.security.Callers.member(headers));
    }

    /** The member looking at a public page, or {@code null} for a visitor (and for staff). */
    public static SellerId viewer(HttpHeaders headers) {
        return org.linlinjava.announce.cqrs.security.Callers.memberIfAny(headers).map(SellerId::new).orElse(null);
    }

    /** The staff member behind an admin call: 401 without an identity, 403 when the caller is not staff. */
    public static Integer staff(HttpHeaders headers) {
        return org.linlinjava.announce.cqrs.security.Callers.staff(headers);
    }

    /** The staff member behind an admin call that needs a permission: as {@link #staff(HttpHeaders)}, then 403 {@code missing-permission}. */
    public static Integer staff(HttpHeaders headers, String permission) {
        return org.linlinjava.announce.cqrs.security.Callers.staff(headers, permission);
    }

    // ---- from the Reactor context (CallerContextFilter) ------------------------------------------------------

    /** The member behind a write, from the context: 401 without an identity, 403 when the caller is not a member. */
    public static Mono<SellerId> member() {
        return resolution().map(resolution -> {
            Caller caller = resolution.required();
            if (!caller.member()) {
                throw new CommandRejectedException(HttpStatus.FORBIDDEN, NOT_A_MEMBER, "Only a member can do this");
            }
            return new SellerId(caller.id());
        });
    }

    /** The member looking at a public page, from the context; empty for a visitor and for staff. */
    public static Mono<SellerId> viewer() {
        return resolution().flatMap(resolution -> {
            Caller caller = resolution.optional();
            return caller != null && caller.member() ? Mono.just(new SellerId(caller.id())) : Mono.empty();
        });
    }

    /** The staff member behind an admin call, from the context: 401, 403 {@code not-staff}, 403 {@code missing-permission}. */
    public static Mono<Integer> staff(String permission) {
        return resolution().map(resolution -> {
            Caller caller = resolution.required();
            if (!caller.staff()) {
                throw new CommandRejectedException(HttpStatus.FORBIDDEN, NOT_STAFF, "Only staff can do this");
            }
            if (!caller.can(permission)) {
                throw new CommandRejectedException(HttpStatus.FORBIDDEN, MISSING_PERMISSION, "This action needs the permission " + permission);
            }
            return caller.id();
        });
    }

    private static Mono<Resolution> resolution() {
        return Mono.deferContextual(context -> context.hasKey(CONTEXT_KEY) ? Mono.just(context.<Resolution>get(CONTEXT_KEY))
                : Mono.error(new IllegalStateException("No caller in the Reactor context: is " + CallerContextFilter.class.getSimpleName() + " installed?")));
    }

    /**
     * What {@link CallerContextFilter} resolved from the identity headers: the caller, nobody, or the failure the
     * headers caused (an id that is not a number, an unknown type). The failure is raised only when a route asks,
     * so a public route served to a malformed identity behaves as the header-based overloads always did.
     */
    public record Resolution(Caller caller, RuntimeException failure) {

        public static final Resolution ANONYMOUS = new Resolution(null, null);

        public static Resolution of(HttpHeaders headers) {
            try {
                return org.linlinjava.announce.cqrs.security.Callers.caller(headers).map(c -> new Resolution(c, null)).orElse(ANONYMOUS);
            } catch (RuntimeException e) {
                return new Resolution(null, e);
            }
        }

        /** The caller or {@code null} for an anonymous request; raises the parse failure, if any. */
        Caller optional() {
            if (failure != null) {
                throw failure;
            }
            return caller;
        }

        /** The caller; 401 {@code no-principal} for an anonymous request. */
        Caller required() {
            Caller resolved = optional();
            if (resolved == null) {
                throw new CommandRejectedException(HttpStatus.UNAUTHORIZED, NO_PRINCIPAL, "Sign in (" + USER_HEADER + ")");
            }
            return resolved;
        }
    }
}
