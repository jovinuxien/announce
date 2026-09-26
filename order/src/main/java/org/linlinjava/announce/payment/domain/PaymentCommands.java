package org.linlinjava.announce.payment.domain;

import com.opencqrs.framework.command.Command;
import org.linlinjava.announce.cqrs.command.Subjects;

import java.time.LocalDateTime;

/**
 * Commands of the Payment aggregate on {@code /payment/{orderId}} and of the SellerAccount aggregate on
 * {@code /seller-account/{userId}}. The provider is called by the application service <em>before</em> the command,
 * which then records the outcome; provider webhooks arrive as {@link RecordProviderEvent}.
 */
public final class PaymentCommands {

    public static final String AGGREGATE = "payment";
    public static final String SELLER_AGGREGATE = "seller-account";

    private PaymentCommands() {}

    public static String subjectOf(String orderId) {
        return Subjects.of(AGGREGATE, orderId);
    }

    public static String sellerSubjectOf(Integer userId) {
        return Subjects.of(SELLER_AGGREGATE, userId);
    }

    public record CreateIntent(String orderId, String announceId, Integer buyerId, Integer sellerId, long amountCents, long feeCents,
                               long payoutCents, String currency, String provider, String intentRef, String clientSecret) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.PRISTINE; }
    }

    /** A verified webhook; {@code providerEventId} makes it idempotent. {@code ref} is the payout or refund reference when the kind carries one. */
    public record RecordProviderEvent(String orderId, String providerEventId, ProviderEventKind kind, String ref, String reason, LocalDateTime at) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    public record RequestPayout(String orderId, long payoutCents, String payoutRef) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    public record RequestRefund(String orderId, long amountCents, String refundRef, String reason) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    /** From OrderCancelledV1; a no-op unless the intent is still open. */
    public record CancelIntent(String orderId) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    public record StartOnboarding(Integer userId, String provider, String accountRef, String onboardingUrl, String status, boolean payoutsEnabled) implements Command {
        @Override public String getSubject() { return sellerSubjectOf(userId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.PRISTINE; }
    }

    public record RecordAccountEvent(Integer userId, String providerEventId, String status, boolean payoutsEnabled, LocalDateTime at) implements Command {
        @Override public String getSubject() { return sellerSubjectOf(userId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }
}
