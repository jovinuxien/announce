package org.linlinjava.announce.goods.interfaces.rest;

import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.goods.domain.announce.SellerId;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * Who is calling, read from the identity headers an edge injects after it authenticated the caller:
 * {@code X-User-Id} with {@code X-User-Type=MEMBER} (customer edge, {@code gateway-api}) or
 * {@code X-User-Type=ADMIN} (staff edge, {@code gateway-admin}). The headers are trustworthy because the
 * framework's {@code ServiceTokenWebFilter} ({@code announce.service-token.mode=enforce}) refuses every request
 * that did not pass an edge. A member is the seller of the announces they post; staff ids and member ids are
 * different number spaces, so the type is always checked.
 */
public final class Callers {

    public static final String USER_HEADER = "X-User-Id";
    public static final String TYPE_HEADER = "X-User-Type";
    public static final String MEMBER = "MEMBER";
    public static final String ADMIN = "ADMIN";
    public static final String NO_PRINCIPAL = "no-principal";
    public static final String NOT_A_MEMBER = "not-a-member";
    public static final String NOT_STAFF = "not-staff";

    private Callers() {
    }

    /** The member behind a write: 401 without an identity, 403 when the caller is not a member. */
    public static SellerId member(HttpHeaders headers) {
        Integer id = id(headers);
        if (id == null) {
            throw new CommandRejectedException(HttpStatus.UNAUTHORIZED, NO_PRINCIPAL, "Sign in as a member (" + USER_HEADER + ")");
        }
        if (!MEMBER.equals(headers.getFirst(TYPE_HEADER))) {
            throw new CommandRejectedException(HttpStatus.FORBIDDEN, NOT_A_MEMBER, "Only a member can own an announce");
        }
        return new SellerId(id);
    }

    /** The member looking at a public page, or {@code null} for a visitor (and for anyone who is not a member). */
    public static SellerId viewer(HttpHeaders headers) {
        Integer id = id(headers);
        return id != null && MEMBER.equals(headers.getFirst(TYPE_HEADER)) ? new SellerId(id) : null;
    }

    /** The staff member behind an admin call: 401 without an identity, 403 when the caller is not staff. */
    public static Integer staff(HttpHeaders headers) {
        Integer id = id(headers);
        if (id == null) {
            throw new CommandRejectedException(HttpStatus.UNAUTHORIZED, NO_PRINCIPAL, "Sign in as staff (" + USER_HEADER + ")");
        }
        if (!ADMIN.equals(headers.getFirst(TYPE_HEADER))) {
            throw new CommandRejectedException(HttpStatus.FORBIDDEN, NOT_STAFF, "Only staff can use the admin API");
        }
        return id;
    }

    private static Integer id(HttpHeaders headers) {
        String value = headers.getFirst(USER_HEADER);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(USER_HEADER + " must be a number");
        }
    }
}
