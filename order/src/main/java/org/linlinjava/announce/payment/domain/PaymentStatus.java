package org.linlinjava.announce.payment.domain;

/** INTENT_CREATED -> CONFIRMED -> RELEASING -> PAID_OUT; or CONFIRMED -> REFUNDING -> REFUNDED; FAILED keeps the intent open; CANCELLED voids it. */
public enum PaymentStatus {
    INTENT_CREATED, CONFIRMED, FAILED, RELEASING, PAID_OUT, REFUNDING, REFUNDED, CANCELLED
}
