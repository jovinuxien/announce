package org.linlinjava.announce.order.application;

import org.junit.jupiter.api.Test;
import org.linlinjava.announce.db.domain.AnnounceOrder;
import org.linlinjava.announce.db.domain.AnnounceOrderSeller;
import org.linlinjava.announce.db.domain.AnnounceOrderStatusTotal;
import org.linlinjava.announce.order.domain.OrderPolicies;
import org.linlinjava.announce.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayoutsTest {

    static final LocalDateTime T = LocalDateTime.of(2026, 10, 10, 12, 0);
    static final OrderPolicies POLICIES = OrderPolicies.defaults();   // delivery grace 3 d, dispatch grace 14 d, release on DELIVERY

    private static AnnounceOrder order(String status) {
        AnnounceOrder o = new AnnounceOrder();
        o.setId("o-1");
        o.setAnnounceId("a-1");
        o.setTitle("Bike");
        o.setSellerId(7);
        o.setBuyerId(9);
        o.setAgreedPrice(new BigDecimal("100.00"));
        o.setFeeAmount(new BigDecimal("8.00"));
        o.setPayoutAmount(new BigDecimal("92.00"));
        o.setCurrency("DKK");
        o.setStatus(status);
        o.setPaidAt(T);
        return o;
    }

    private static AnnounceOrderStatusTotal total(String status, long orders, String fee, String payout) {
        AnnounceOrderStatusTotal t = new AnnounceOrderStatusTotal();
        t.setStatus(status);
        t.setOrders(orders);
        t.setFeeAmount(new BigDecimal(fee));
        t.setPayoutAmount(new BigDecimal(payout));
        return t;
    }

    @Test
    void everyOrderStatusThatHoldsMoneyMapsToOnePayoutStatusAndTheOthersToNone() {
        assertThat(Payouts.PayoutStatus.of(OrderStatus.PAID)).contains(Payouts.PayoutStatus.PENDING_RELEASE);
        assertThat(Payouts.PayoutStatus.of(OrderStatus.DELIVERED)).contains(Payouts.PayoutStatus.PENDING_RELEASE);
        assertThat(Payouts.PayoutStatus.of(OrderStatus.DISPUTED)).contains(Payouts.PayoutStatus.BLOCKED_DISPUTE);
        assertThat(Payouts.PayoutStatus.of(OrderStatus.RELEASING)).contains(Payouts.PayoutStatus.RELEASING);
        assertThat(Payouts.PayoutStatus.of(OrderStatus.SETTLED)).contains(Payouts.PayoutStatus.SENT);
        assertThat(Payouts.PayoutStatus.of(OrderStatus.REFUNDING)).contains(Payouts.PayoutStatus.REFUNDED);
        for (OrderStatus none : List.of(OrderStatus.PROPOSED, OrderStatus.AGREED, OrderStatus.PAYMENT_PENDING, OrderStatus.CANCELLED, OrderStatus.DECLINED)) {
            assertThat(Payouts.PayoutStatus.of(none)).as(none.name()).isEmpty();
        }
        assertThat(Payouts.PayoutStatus.allOrderStatusNames()).containsExactlyInAnyOrder(
                "PAID", "SHIPPED", "DELIVERED", "DISPUTED", "RELEASING", "SETTLED", "REFUNDING", "REFUNDED");
        assertThatThrownBy(() -> Payouts.row(order("AGREED"), null, POLICIES)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theRowCarriesTheSellerGateAndWhenTheReleaseRuleFires() {
        AnnounceOrderSeller gate = new AnnounceOrderSeller();
        gate.setAccountStatus("VERIFIED");
        gate.setPayoutsEnabled(true);

        AnnounceOrder delivered = order("DELIVERED");
        delivered.setDeliveredAt(T);
        Payouts.PayoutRow row = Payouts.row(delivered, gate, POLICIES);
        assertThat(row.status()).isEqualTo(Payouts.PayoutStatus.PENDING_RELEASE);
        assertThat(row.sellerAccountStatus()).isEqualTo("VERIFIED");
        assertThat(row.payoutsEnabled()).isTrue();
        assertThat(row.payoutAmount()).isEqualByComparingTo("92.00");
        assertThat(row.releaseDueAt()).as("delivery grace after delivery").isEqualTo(T.plus(POLICIES.deliveryGrace()));

        AnnounceOrder shipped = order("SHIPPED");
        shipped.setShippedAt(T);
        assertThat(Payouts.row(shipped, gate, POLICIES).releaseDueAt()).as("release on delivery: the dispatch grace").isEqualTo(T.plus(POLICIES.dispatchGrace()));
        assertThat(Payouts.row(shipped, gate, POLICIES.withReleaseOn(OrderPolicies.ReleaseOn.DISPATCH)).releaseDueAt())
                .as("release on dispatch: the delivery grace counted from dispatch").isEqualTo(T.plus(POLICIES.deliveryGrace()));

        assertThat(Payouts.row(order("PAID"), gate, POLICIES).releaseDueAt()).as("nothing scheduled before dispatch").isNull();
        AnnounceOrder disputed = order("DISPUTED");
        disputed.setDisputeReason("cracked frame");
        Payouts.PayoutRow blocked = Payouts.row(disputed, null, POLICIES);
        assertThat(blocked.status()).isEqualTo(Payouts.PayoutStatus.BLOCKED_DISPUTE);
        assertThat(blocked.releaseDueAt()).isNull();
        assertThat(blocked.sellerAccountStatus()).as("no gate row yet").isNull();
        assertThat(blocked.payoutsEnabled()).isFalse();
        assertThat(blocked.disputeReason()).isEqualTo("cracked frame");
    }

    @Test
    void totalsBucketTheStatusSumsAndIgnoreStatusesThatHoldNoMoney() {
        Payouts.PayoutTotals totals = Payouts.totals(List.of(
                total("PAID", 2, "10.00", "90.00"), total("DELIVERED", 1, "5.00", "45.00"),
                total("DISPUTED", 1, "4.00", "36.00"), total("RELEASING", 1, "3.00", "27.00"),
                total("SETTLED", 3, "30.00", "270.00"), total("REFUNDED", 1, "2.00", "18.00"),
                total("AGREED", 5, "50.00", "450.00"), total("CANCELLED", 2, "1.00", "9.00"), total("IT_UNKNOWN", 1, "1.00", "1.00")), "DKK");
        assertThat(totals.pendingAmount()).isEqualByComparingTo("135.00");
        assertThat(totals.pendingOrders()).isEqualTo(3);
        assertThat(totals.blockedAmount()).isEqualByComparingTo("36.00");
        assertThat(totals.releasingAmount()).isEqualByComparingTo("27.00");
        assertThat(totals.sentAmount()).isEqualByComparingTo("270.00");
        assertThat(totals.sentOrders()).isEqualTo(3);
        assertThat(totals.refundedAmount()).isEqualByComparingTo("18.00");
        assertThat(totals.feesCollected()).as("only settled deals collected their fee").isEqualByComparingTo("30.00");
        assertThat(totals.feesPending()).as("pending + blocked + releasing").isEqualByComparingTo("22.00");
        assertThat(totals.currency()).isEqualTo("DKK");
    }
}
