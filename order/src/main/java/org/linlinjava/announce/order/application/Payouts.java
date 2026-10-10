package org.linlinjava.announce.order.application;

import org.linlinjava.announce.db.domain.AnnounceOrder;
import org.linlinjava.announce.db.domain.AnnounceOrderSeller;
import org.linlinjava.announce.db.domain.AnnounceOrderStatusTotal;
import org.linlinjava.announce.order.domain.OrderPolicies;
import org.linlinjava.announce.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The payouts overview, derived from the order read model: there is no payout table, the order row carries the
 * money held for the seller (payout_amount), the platform's fee, the dispute state and every timestamp, and the
 * order context keeps its own copy of whether the seller can be paid ({@code announce_order_seller}). Pure
 * functions, so the derivation is unit-tested without a database.
 */
public final class Payouts {

    private Payouts() {
    }

    /** Where the money of a paid order stands, seen from the seller's side. */
    public enum PayoutStatus {
        /** Paid, maybe shipped or delivered: the release rule has not fired yet. */
        PENDING_RELEASE(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED),
        /** A dispute is open: the release rule refuses until staff resolve it. */
        BLOCKED_DISPUTE(OrderStatus.DISPUTED),
        /** The payout was requested from the provider and its transfer is not confirmed yet. */
        RELEASING(OrderStatus.RELEASING),
        /** The provider confirmed the transfer; the fee is collected. */
        SENT(OrderStatus.SETTLED),
        /** The buyer gets the money back; nothing goes to the seller. */
        REFUNDED(OrderStatus.REFUNDING, OrderStatus.REFUNDED);

        private final List<OrderStatus> orderStatuses;

        PayoutStatus(OrderStatus... orderStatuses) {
            this.orderStatuses = List.of(orderStatuses);
        }

        public List<OrderStatus> orderStatuses() {
            return orderStatuses;
        }

        public List<String> orderStatusNames() {
            return orderStatuses.stream().map(Enum::name).toList();
        }

        /** The payout status of an order status, or empty before payment and for cancelled / declined deals (no money held). */
        public static Optional<PayoutStatus> of(OrderStatus status) {
            for (PayoutStatus p : values()) {
                if (p.orderStatuses.contains(status)) return Optional.of(p);
            }
            return Optional.empty();
        }

        /** Every order status that holds or held money: the rows of the overview without a filter. */
        public static List<String> allOrderStatusNames() {
            return java.util.Arrays.stream(values()).flatMap(p -> p.orderStatuses.stream()).map(Enum::name).toList();
        }
    }

    /** One line of the overview: the order's money plus the seller gate and when the release rule fires. */
    public record PayoutRow(String orderId, String announceId, String title, Integer sellerId, Integer buyerId,
                            String sellerAccountStatus, boolean payoutsEnabled,
                            BigDecimal agreedPrice, BigDecimal feeAmount, BigDecimal payoutAmount, String currency,
                            PayoutStatus status, String orderStatus, LocalDateTime releaseDueAt,
                            LocalDateTime paidAt, LocalDateTime releasedAt, LocalDateTime settledAt,
                            String disputeReason, LocalDateTime disputeOpenedAt) {
    }

    /** Sums over every live order that holds or held money (not only the page shown). */
    public record PayoutTotals(BigDecimal pendingAmount, long pendingOrders, BigDecimal blockedAmount, long blockedOrders,
                               BigDecimal releasingAmount, long releasingOrders, BigDecimal sentAmount, long sentOrders,
                               BigDecimal refundedAmount, long refundedOrders, BigDecimal feesCollected, BigDecimal feesPending,
                               String currency) {
    }

    public static PayoutRow row(AnnounceOrder o, AnnounceOrderSeller gate, OrderPolicies policies) {
        OrderStatus orderStatus = OrderStatus.valueOf(o.getStatus());
        PayoutStatus status = PayoutStatus.of(orderStatus)
                .orElseThrow(() -> new IllegalArgumentException("Order " + o.getId() + " in status " + o.getStatus() + " holds no money"));
        return new PayoutRow(o.getId(), o.getAnnounceId(), o.getTitle(), o.getSellerId(), o.getBuyerId(),
                gate == null ? null : gate.getAccountStatus(), gate != null && Boolean.TRUE.equals(gate.getPayoutsEnabled()),
                o.getAgreedPrice(), o.getFeeAmount(), o.getPayoutAmount(), o.getCurrency(),
                status, o.getStatus(), releaseDueAt(o, orderStatus, policies),
                o.getPaidAt(), o.getReleasedAt(), o.getSettledAt(), o.getDisputeReason(), o.getDisputeOpenedAt());
    }

    /**
     * When the release rule ({@code OrderRules.checkRelease}) fires on its own for a pending order: the delivery
     * grace after delivery, or after dispatch when releasing on dispatch, else the dispatch grace after dispatch
     * (the buyer's confirmation releases earlier, and is shown as its own timestamp). Null when nothing is
     * scheduled yet (paid, not shipped) and for every other status.
     */
    static LocalDateTime releaseDueAt(AnnounceOrder o, OrderStatus status, OrderPolicies p) {
        return switch (status) {
            case DELIVERED -> o.getDeliveredAt() == null ? null : o.getDeliveredAt().plus(p.deliveryGrace());
            case SHIPPED -> o.getShippedAt() == null ? null
                    : o.getShippedAt().plus(p.releaseOn() == OrderPolicies.ReleaseOn.DISPATCH ? p.deliveryGrace() : p.dispatchGrace());
            default -> null;
        };
    }

    public static PayoutTotals totals(Collection<AnnounceOrderStatusTotal> perStatus, String currency) {
        Map<PayoutStatus, BigDecimal> amount = new EnumMap<>(PayoutStatus.class);
        Map<PayoutStatus, Long> count = new EnumMap<>(PayoutStatus.class);
        Map<PayoutStatus, BigDecimal> fee = new EnumMap<>(PayoutStatus.class);
        for (PayoutStatus p : PayoutStatus.values()) {
            amount.put(p, BigDecimal.ZERO);
            count.put(p, 0L);
            fee.put(p, BigDecimal.ZERO);
        }
        for (AnnounceOrderStatusTotal t : perStatus) {
            OrderStatus os;
            try {
                os = OrderStatus.valueOf(t.getStatus());
            } catch (IllegalArgumentException unknown) {
                continue;
            }
            PayoutStatus.of(os).ifPresent(p -> {
                amount.merge(p, nz(t.getPayoutAmount()), BigDecimal::add);
                count.merge(p, t.getOrders() == null ? 0L : t.getOrders(), Long::sum);
                fee.merge(p, nz(t.getFeeAmount()), BigDecimal::add);
            });
        }
        BigDecimal feesPending = fee.get(PayoutStatus.PENDING_RELEASE).add(fee.get(PayoutStatus.BLOCKED_DISPUTE)).add(fee.get(PayoutStatus.RELEASING));
        return new PayoutTotals(amount.get(PayoutStatus.PENDING_RELEASE), count.get(PayoutStatus.PENDING_RELEASE),
                amount.get(PayoutStatus.BLOCKED_DISPUTE), count.get(PayoutStatus.BLOCKED_DISPUTE),
                amount.get(PayoutStatus.RELEASING), count.get(PayoutStatus.RELEASING),
                amount.get(PayoutStatus.SENT), count.get(PayoutStatus.SENT),
                amount.get(PayoutStatus.REFUNDED), count.get(PayoutStatus.REFUNDED),
                fee.get(PayoutStatus.SENT), feesPending, currency);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
