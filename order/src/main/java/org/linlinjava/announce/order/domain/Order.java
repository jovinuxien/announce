package org.linlinjava.announce.order.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.linlinjava.announce.order.domain.events.DisputeOpened;
import org.linlinjava.announce.order.domain.events.DisputeResolved;
import org.linlinjava.announce.order.domain.events.OrderAgreed;
import org.linlinjava.announce.order.domain.events.OrderCancelled;
import org.linlinjava.announce.order.domain.events.OrderDeclined;
import org.linlinjava.announce.order.domain.events.OrderDelivered;
import org.linlinjava.announce.order.domain.events.OrderPaid;
import org.linlinjava.announce.order.domain.events.OrderPaymentFailed;
import org.linlinjava.announce.order.domain.events.OrderPaymentIntentCreated;
import org.linlinjava.announce.order.domain.events.OrderProposed;
import org.linlinjava.announce.order.domain.events.OrderRefunded;
import org.linlinjava.announce.order.domain.events.OrderSettled;
import org.linlinjava.announce.order.domain.events.OrderShipped;
import org.linlinjava.announce.order.domain.events.PayoutReleaseRequested;
import org.linlinjava.announce.order.domain.events.ReceiptConfirmed;
import org.linlinjava.announce.order.domain.events.RefundRequested;

import java.time.LocalDateTime;

/**
 * State of the Order aggregate under {@code /order/{orderId}}: one deal = one announce, one buyer, one seller
 * ({@code doc/order-context.md} §4). It never talks to a provider: it records what the payment and shipping contexts
 * report and owns the release decision. Immutable; the rules live in {@link OrderRules}. Amounts are minor units.
 */
public record Order(String id,
                    String announceId,
                    Integer buyerId,
                    Integer sellerId,
                    Door door,
                    String auctionId,
                    String conversationId,
                    long priceCents,
                    long feeCents,
                    long payoutCents,
                    String currency,
                    String title,
                    String mainPicture,
                    Integer categoryId,
                    OrderStatus status,
                    OrderStatus statusBeforeDispute,
                    String paymentId,
                    String shipmentId,
                    String trackingNumber,
                    LocalDateTime proposedAt,
                    LocalDateTime agreedAt,
                    LocalDateTime paidAt,
                    LocalDateTime shippedAt,
                    LocalDateTime deliveredAt,
                    LocalDateTime receiptConfirmedAt,
                    LocalDateTime releasedAt,
                    LocalDateTime settledAt,
                    LocalDateTime closedAt,
                    String disputeReason,
                    LocalDateTime disputeOpenedAt,
                    String disputeResolution,
                    LocalDateTime updatedAt) {

    @JsonIgnore
    public boolean isParty(Integer userId) {
        return userId != null && (userId.equals(buyerId) || userId.equals(sellerId));
    }

    // ------------------------------------------------------------------ state rebuilding

    public static Order apply(String id, OrderProposed e) {
        return new Order(id, e.announceId(), e.buyerId(), e.sellerId(), Door.FIXED_PRICE, null, null, e.priceCents(), 0, 0, e.currency(),
                e.title(), e.mainPicture(), e.categoryId(), OrderStatus.PROPOSED, null, null, null, null, e.at(), null, null, null, null, null,
                null, null, null, null, null, null, e.at());
    }

    public static Order apply(String id, OrderAgreed e) {
        return new Order(id, e.announceId(), e.buyerId(), e.sellerId(), Door.valueOf(e.door()), e.auctionId(), e.conversationId(),
                e.priceCents(), e.feeCents(), e.payoutCents(), e.currency(), e.title(), e.mainPicture(), null, OrderStatus.AGREED, null,
                null, null, null, e.at(), e.at(), null, null, null, null, null, null, null, null, null, null, e.at());
    }

    public Order apply(OrderAgreed e) {
        return new Order(id, announceId, buyerId, sellerId, Door.valueOf(e.door()), e.auctionId(), e.conversationId(), e.priceCents(),
                e.feeCents(), e.payoutCents(), e.currency(), e.title(), e.mainPicture(), categoryId, OrderStatus.AGREED, null, paymentId,
                shipmentId, trackingNumber, proposedAt, e.at(), paidAt, shippedAt, deliveredAt, receiptConfirmedAt, releasedAt, settledAt,
                closedAt, disputeReason, disputeOpenedAt, disputeResolution, e.at());
    }

    public Order apply(OrderDeclined e) { return closed(OrderStatus.DECLINED, e.at()); }
    public Order apply(OrderPaymentIntentCreated e) { return with(OrderStatus.PAYMENT_PENDING, e.at()).withPayment(e.paymentId()); }
    public Order apply(OrderPaid e) { return with(OrderStatus.PAID, e.at()).withPayment(e.paymentId()).withTimes(e.at(), shippedAt, deliveredAt, receiptConfirmedAt, releasedAt, settledAt); }
    public Order apply(OrderPaymentFailed e) { return with(status, e.at()); }
    public Order apply(OrderShipped e) { return with(OrderStatus.SHIPPED, e.at()).withShipment(e.shipmentId(), e.trackingNumber()).withTimes(paidAt, e.at(), deliveredAt, receiptConfirmedAt, releasedAt, settledAt); }
    public Order apply(OrderDelivered e) { return with(status == OrderStatus.DISPUTED ? OrderStatus.DISPUTED : OrderStatus.DELIVERED, e.at()).withBefore(status == OrderStatus.DISPUTED ? OrderStatus.DELIVERED : statusBeforeDispute).withTimes(paidAt, shippedAt, e.at(), receiptConfirmedAt, releasedAt, settledAt); }
    public Order apply(ReceiptConfirmed e) { return with(status, e.at()).withTimes(paidAt, shippedAt, deliveredAt, e.at(), releasedAt, settledAt); }
    public Order apply(DisputeOpened e) { return with(OrderStatus.DISPUTED, e.at()).withBefore(status).withDispute(e.reason(), e.at(), null); }
    public Order apply(DisputeResolved e) {
        OrderStatus next = "REFUND".equals(e.resolution()) ? OrderStatus.REFUNDING : OrderStatus.RELEASING;
        return with(next, e.at()).withDispute(disputeReason, disputeOpenedAt, e.resolution());
    }
    public Order apply(PayoutReleaseRequested e) { return with(OrderStatus.RELEASING, e.at()).withTimes(paidAt, shippedAt, deliveredAt, receiptConfirmedAt, e.at(), settledAt); }
    public Order apply(OrderSettled e) { return with(OrderStatus.SETTLED, e.at()).withTimes(paidAt, shippedAt, deliveredAt, receiptConfirmedAt, releasedAt, e.at()); }
    public Order apply(OrderCancelled e) { return closed(OrderStatus.CANCELLED, e.at()); }
    public Order apply(RefundRequested e) { return with(OrderStatus.REFUNDING, e.at()); }
    public Order apply(OrderRefunded e) { return closed(OrderStatus.REFUNDED, e.at()); }

    // ------------------------------------------------------------------ helpers

    private Order with(OrderStatus newStatus, LocalDateTime at) {
        return new Order(id, announceId, buyerId, sellerId, door, auctionId, conversationId, priceCents, feeCents, payoutCents, currency, title,
                mainPicture, categoryId, newStatus, statusBeforeDispute, paymentId, shipmentId, trackingNumber, proposedAt, agreedAt, paidAt,
                shippedAt, deliveredAt, receiptConfirmedAt, releasedAt, settledAt, closedAt, disputeReason, disputeOpenedAt, disputeResolution, at);
    }

    private Order closed(OrderStatus newStatus, LocalDateTime at) {
        return new Order(id, announceId, buyerId, sellerId, door, auctionId, conversationId, priceCents, feeCents, payoutCents, currency, title,
                mainPicture, categoryId, newStatus, statusBeforeDispute, paymentId, shipmentId, trackingNumber, proposedAt, agreedAt, paidAt,
                shippedAt, deliveredAt, receiptConfirmedAt, releasedAt, settledAt, at, disputeReason, disputeOpenedAt, disputeResolution, at);
    }

    private Order withPayment(String newPaymentId) {
        return new Order(id, announceId, buyerId, sellerId, door, auctionId, conversationId, priceCents, feeCents, payoutCents, currency, title,
                mainPicture, categoryId, status, statusBeforeDispute, newPaymentId == null ? paymentId : newPaymentId, shipmentId, trackingNumber,
                proposedAt, agreedAt, paidAt, shippedAt, deliveredAt, receiptConfirmedAt, releasedAt, settledAt, closedAt, disputeReason,
                disputeOpenedAt, disputeResolution, updatedAt);
    }

    private Order withShipment(String newShipmentId, String tracking) {
        return new Order(id, announceId, buyerId, sellerId, door, auctionId, conversationId, priceCents, feeCents, payoutCents, currency, title,
                mainPicture, categoryId, status, statusBeforeDispute, paymentId, newShipmentId, tracking, proposedAt, agreedAt, paidAt, shippedAt,
                deliveredAt, receiptConfirmedAt, releasedAt, settledAt, closedAt, disputeReason, disputeOpenedAt, disputeResolution, updatedAt);
    }

    private Order withBefore(OrderStatus before) {
        return new Order(id, announceId, buyerId, sellerId, door, auctionId, conversationId, priceCents, feeCents, payoutCents, currency, title,
                mainPicture, categoryId, status, before, paymentId, shipmentId, trackingNumber, proposedAt, agreedAt, paidAt, shippedAt,
                deliveredAt, receiptConfirmedAt, releasedAt, settledAt, closedAt, disputeReason, disputeOpenedAt, disputeResolution, updatedAt);
    }

    private Order withTimes(LocalDateTime paid, LocalDateTime shipped, LocalDateTime delivered, LocalDateTime confirmed, LocalDateTime released, LocalDateTime settled) {
        return new Order(id, announceId, buyerId, sellerId, door, auctionId, conversationId, priceCents, feeCents, payoutCents, currency, title,
                mainPicture, categoryId, status, statusBeforeDispute, paymentId, shipmentId, trackingNumber, proposedAt, agreedAt, paid, shipped,
                delivered, confirmed, released, settled, closedAt, disputeReason, disputeOpenedAt, disputeResolution, updatedAt);
    }

    private Order withDispute(String reason, LocalDateTime openedAt, String resolution) {
        return new Order(id, announceId, buyerId, sellerId, door, auctionId, conversationId, priceCents, feeCents, payoutCents, currency, title,
                mainPicture, categoryId, status, statusBeforeDispute, paymentId, shipmentId, trackingNumber, proposedAt, agreedAt, paidAt, shippedAt,
                deliveredAt, receiptConfirmedAt, releasedAt, settledAt, closedAt, reason, openedAt, resolution, updatedAt);
    }
}
