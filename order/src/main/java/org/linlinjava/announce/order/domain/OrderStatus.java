package org.linlinjava.announce.order.domain;

/**
 * PROPOSED -> AGREED -> PAYMENT_PENDING -> PAID -> SHIPPED -> DELIVERED -> RELEASING -> SETTLED.
 * Exits: DECLINED and CANCELLED before payment; DISPUTED after payment (blocks the release, resolves to RELEASING or
 * REFUNDING); REFUNDING -> REFUNDED.
 */
public enum OrderStatus {
    PROPOSED, AGREED, PAYMENT_PENDING, PAID, SHIPPED, DELIVERED, RELEASING, SETTLED, DISPUTED, REFUNDING, REFUNDED, CANCELLED, DECLINED;

    /** Money has been taken from the buyer and not yet given back or paid out. */
    public boolean isPaidAndOpen() {
        return this == PAID || this == SHIPPED || this == DELIVERED || this == DISPUTED;
    }

    public boolean isBeforePayment() {
        return this == PROPOSED || this == AGREED || this == PAYMENT_PENDING;
    }

    public boolean isFinal() {
        return this == SETTLED || this == REFUNDED || this == CANCELLED || this == DECLINED;
    }
}
