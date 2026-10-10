package org.linlinjava.announce.goods.interfaces.rest;

import org.linlinjava.announce.goods.domain.announce.SellerId;
import org.springframework.http.HttpHeaders;

/**
 * This context's view of the caller: the framework's {@link org.linlinjava.announce.cqrs.security.Callers} reads
 * {@code X-User-Id} and {@code X-User-Type} ({@code MEMBER} from the customer edge, {@code ADMIN} from the admin
 * edge); a member is the {@link SellerId} of the announces they own. The headers are trustworthy because the
 * framework's {@code ServiceTokenWebFilter} refuses every request that did not pass an edge.
 */
public final class Callers {

    public static final String USER_HEADER = org.linlinjava.announce.cqrs.security.Callers.USER_HEADER;
    public static final String TYPE_HEADER = org.linlinjava.announce.cqrs.security.Callers.TYPE_HEADER;
    public static final String MEMBER = org.linlinjava.announce.cqrs.security.Callers.MEMBER;
    public static final String ADMIN = org.linlinjava.announce.cqrs.security.Callers.ADMIN;
    public static final String NO_PRINCIPAL = org.linlinjava.announce.cqrs.security.Callers.NO_PRINCIPAL;
    public static final String NOT_A_MEMBER = org.linlinjava.announce.cqrs.security.Callers.NOT_A_MEMBER;
    public static final String NOT_STAFF = org.linlinjava.announce.cqrs.security.Callers.NOT_STAFF;

    private Callers() {
    }

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
}
