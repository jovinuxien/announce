package org.linlinjava.announce.payment.domain;

/** What a provider webhook reports about a payment. */
public enum ProviderEventKind {
    CONFIRMED, FAILED, PAYOUT_SENT, REFUND_SENT
}
