package org.linlinjava.announce.payment.domain;

import org.linlinjava.announce.payment.domain.events.PaymentCancelled;
import org.linlinjava.announce.payment.domain.events.PaymentConfirmed;
import org.linlinjava.announce.payment.domain.events.PaymentFailed;
import org.linlinjava.announce.payment.domain.events.PaymentIntentCreated;
import org.linlinjava.announce.payment.domain.events.PayoutRequested;
import org.linlinjava.announce.payment.domain.events.PayoutSent;
import org.linlinjava.announce.payment.domain.events.RefundRequested;
import org.linlinjava.announce.payment.domain.events.RefundSent;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * State of the Payment aggregate under {@code /payment/{orderId}}: the provider-side life of one deal's money.
 * Remembers every provider event id it applied so a redelivered webhook is a no-op. Immutable; amounts in minor units.
 */
public record Payment(String orderId,
                      String announceId,
                      Integer buyerId,
                      Integer sellerId,
                      String provider,
                      String intentRef,
                      String clientSecret,
                      long amountCents,
                      long feeCents,
                      long payoutCents,
                      String currency,
                      PaymentStatus status,
                      String payoutRef,
                      String refundRef,
                      long refundAmountCents,
                      String failureReason,
                      Set<String> providerEventIds,
                      LocalDateTime confirmedAt,
                      LocalDateTime releasedAt,
                      LocalDateTime refundedAt,
                      LocalDateTime updatedAt) {

    public Payment {
        providerEventIds = providerEventIds == null ? Set.of() : Set.copyOf(providerEventIds);
    }

    public boolean hasSeen(String providerEventId) {
        return providerEventId != null && providerEventIds.contains(providerEventId);
    }

    public static Payment apply(String orderId, PaymentIntentCreated e) {
        return new Payment(orderId, e.announceId(), e.buyerId(), e.sellerId(), e.provider(), e.intentRef(), e.clientSecret(), e.amountCents(),
                e.feeCents(), e.payoutCents(), e.currency(), PaymentStatus.INTENT_CREATED, null, null, 0L, null, Set.of(), null, null, null, e.at());
    }

    public Payment apply(PaymentConfirmed e) { return with(PaymentStatus.CONFIRMED, e.providerEventId(), e.at()).times(e.at(), releasedAt, refundedAt); }
    public Payment apply(PaymentFailed e) { return with(PaymentStatus.FAILED, e.providerEventId(), e.at()).failure(e.reason()); }
    public Payment apply(PayoutRequested e) { return with(PaymentStatus.RELEASING, null, e.at()).refs(e.payoutRef(), refundRef, refundAmountCents); }
    public Payment apply(PayoutSent e) { return with(PaymentStatus.PAID_OUT, e.providerEventId(), e.at()).refs(e.payoutRef(), refundRef, refundAmountCents).times(confirmedAt, e.at(), refundedAt); }
    public Payment apply(RefundRequested e) { return with(PaymentStatus.REFUNDING, null, e.at()).refs(payoutRef, e.refundRef(), e.amountCents()); }
    public Payment apply(RefundSent e) { return with(PaymentStatus.REFUNDED, e.providerEventId(), e.at()).refs(payoutRef, e.refundRef(), refundAmountCents).times(confirmedAt, releasedAt, e.at()); }
    public Payment apply(PaymentCancelled e) { return with(PaymentStatus.CANCELLED, null, e.at()); }

    private Payment with(PaymentStatus newStatus, String providerEventId, LocalDateTime at) {
        Set<String> ids = new LinkedHashSet<>(providerEventIds);
        if (providerEventId != null) ids.add(providerEventId);
        return new Payment(orderId, announceId, buyerId, sellerId, provider, intentRef, clientSecret, amountCents, feeCents, payoutCents, currency,
                newStatus, payoutRef, refundRef, refundAmountCents, failureReason, ids, confirmedAt, releasedAt, refundedAt, at);
    }

    private Payment refs(String payout, String refund, long refundAmount) {
        return new Payment(orderId, announceId, buyerId, sellerId, provider, intentRef, clientSecret, amountCents, feeCents, payoutCents, currency,
                status, payout, refund, refundAmount, failureReason, providerEventIds, confirmedAt, releasedAt, refundedAt, updatedAt);
    }

    private Payment times(LocalDateTime confirmed, LocalDateTime released, LocalDateTime refunded) {
        return new Payment(orderId, announceId, buyerId, sellerId, provider, intentRef, clientSecret, amountCents, feeCents, payoutCents, currency,
                status, payoutRef, refundRef, refundAmountCents, failureReason, providerEventIds, confirmed, released, refunded, updatedAt);
    }

    private Payment failure(String reason) {
        return new Payment(orderId, announceId, buyerId, sellerId, provider, intentRef, clientSecret, amountCents, feeCents, payoutCents, currency,
                status, payoutRef, refundRef, refundAmountCents, reason, providerEventIds, confirmedAt, releasedAt, refundedAt, updatedAt);
    }
}
