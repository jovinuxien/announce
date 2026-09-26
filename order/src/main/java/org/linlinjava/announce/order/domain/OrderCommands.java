package org.linlinjava.announce.order.domain;

import com.opencqrs.framework.command.Command;
import org.linlinjava.announce.cqrs.command.Subjects;

/**
 * Commands of the Order aggregate on {@code /order/{orderId}} ({@code doc/order-context.md} §4). Members and staff
 * act over REST; the {@code Record*} commands carry facts reported by the payment and shipping contexts through
 * their topics; the {@code Expire*} and {@code ReleasePayout} commands come from the jobs and are idempotent.
 * Amounts are minor units.
 */
public final class OrderCommands {

    public static final String AGGREGATE = "order";

    private OrderCommands() {}

    public static String subjectOf(String orderId) {
        return Subjects.of(AGGREGATE, orderId);
    }

    /** Fixed-price door, step 1: the buyer asks; the announce snapshot supplies seller, price and title. */
    public record RequestPurchase(String orderId, String announceId, Integer buyerId, Integer sellerId, long priceCents, String currency,
                                  String title, String mainPicture, Integer categoryId) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.PRISTINE; }
    }

    /** Fixed-price door, step 2: the seller accepts and the fee is fixed. */
    public record AcceptPurchase(String orderId, Integer sellerId) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    public record DeclinePurchase(String orderId, Integer sellerId, String reason) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    /** Auction door: the order is created AGREED at the winning bid or the snag-it price. */
    public record CreateFromAuction(String orderId, String announceId, Integer buyerId, Integer sellerId, long priceCents, String currency,
                                    String title, String mainPicture, Integer categoryId, String auctionId) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.PRISTINE; }
    }

    /** Chat-offer door: the order is created AGREED at the accepted offer. */
    public record CreateFromOffer(String orderId, String announceId, Integer buyerId, Integer sellerId, long priceCents, String currency,
                                  String title, String mainPicture, Integer categoryId, String conversationId) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.PRISTINE; }
    }

    public record RecordPaymentIntent(String orderId, String paymentId) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    public record RecordPaymentConfirmed(String orderId, String paymentId) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    public record RecordPaymentFailed(String orderId, String reason) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    public record RecordShipmentDispatched(String orderId, String shipmentId, String trackingNumber) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    public record RecordShipmentDelivered(String orderId) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    /** Buyer: ends the dispute window early; releases the payout when the release rule passes. */
    public record ConfirmReceipt(String orderId, Integer buyerId) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    /** {@code openedByType} USER (the buyer, after payment) or SYSTEM (the carrier reported the parcel failed). */
    public record OpenDispute(String orderId, Integer openedBy, String openedByType, String reason) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    /** Staff: {@code resolution} RELEASE or REFUND. */
    public record ResolveDispute(String orderId, Integer adminId, String resolution, String note) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    /**
     * Job or staff: asks for the payout. Rejected while a dispute is open or before the carrier confirmed delivery
     * (or dispatch, per {@code order.release.on}); a no-op while the grace has not elapsed.
     */
    public record ReleasePayout(String orderId) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    public record RecordPayoutSent(String orderId, String payoutRef) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    /** Buyer or seller before payment ({@code actorType} USER); the pay-window job ({@code SYSTEM}, a no-op unless due). */
    public record Cancel(String orderId, Integer actorId, String actorType, String reason) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    /** Job: a paid order the seller did not ship in time is refunded; a no-op unless due. */
    public record ExpireShipWindow(String orderId) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }

    public record RecordRefunded(String orderId, String refundRef) implements Command {
        @Override public String getSubject() { return subjectOf(orderId); }
        @Override public SubjectCondition getSubjectCondition() { return SubjectCondition.EXISTS; }
    }
}
