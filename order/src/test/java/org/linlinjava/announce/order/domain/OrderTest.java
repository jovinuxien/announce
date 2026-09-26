package org.linlinjava.announce.order.domain;

import org.junit.jupiter.api.Test;
import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.cqrs.test.CommandFixture;
import org.linlinjava.announce.order.domain.OrderCommands.AcceptPurchase;
import org.linlinjava.announce.order.domain.OrderCommands.Cancel;
import org.linlinjava.announce.order.domain.OrderCommands.ConfirmReceipt;
import org.linlinjava.announce.order.domain.OrderCommands.CreateFromAuction;
import org.linlinjava.announce.order.domain.OrderCommands.ExpireShipWindow;
import org.linlinjava.announce.order.domain.OrderCommands.OpenDispute;
import org.linlinjava.announce.order.domain.OrderCommands.RecordPaymentConfirmed;
import org.linlinjava.announce.order.domain.OrderCommands.ReleasePayout;
import org.linlinjava.announce.order.domain.OrderCommands.RequestPurchase;
import org.linlinjava.announce.order.domain.OrderCommands.ResolveDispute;
import org.linlinjava.announce.order.domain.events.DisputeOpened;
import org.linlinjava.announce.order.domain.events.DisputeResolved;
import org.linlinjava.announce.order.domain.events.OrderAgreed;
import org.linlinjava.announce.order.domain.events.OrderCancelled;
import org.linlinjava.announce.order.domain.events.OrderDelivered;
import org.linlinjava.announce.order.domain.events.OrderPaid;
import org.linlinjava.announce.order.domain.events.OrderPaymentIntentCreated;
import org.linlinjava.announce.order.domain.events.OrderProposed;
import org.linlinjava.announce.order.domain.events.OrderShipped;
import org.linlinjava.announce.order.domain.events.PayoutReleaseRequested;
import org.linlinjava.announce.order.domain.events.ReceiptConfirmed;
import org.linlinjava.announce.order.domain.events.RefundRequested;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Order aggregate: the three doors, the fee, the state machine and the release rule of doc/order-context.md §3 and §4. */
class OrderTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"), ZoneOffset.UTC);
    static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    static final String ID = "o-1";
    static final int BUYER = 9;
    static final int SELLER = 7;
    static final OrderPolicies POLICIES = OrderPolicies.defaults();   // 8% + 500, release on DELIVERY, grace 3d / 14d

    static CommandFixture<Order> fixture(OrderPolicies policies) {
        return CommandFixture.forAggregate(Order.class).handlers(OrderHandlers.definitions(CLOCK, policies)).subject(OrderCommands.subjectOf(ID));
    }

    static CommandFixture<Order> fixture() {
        return fixture(POLICIES);
    }

    static OrderAgreed agreed(LocalDateTime at) {
        return new OrderAgreed("a-1", BUYER, SELLER, "FIXED_PRICE", null, null, 5000, 900, 4100, "DKK", "Bike", "", at);
    }

    static OrderPaid paid(LocalDateTime at) {
        return new OrderPaid("p-1", at);
    }

    @Test
    void theFixedPriceDoorFixesTheFeeAtAcceptance() {
        fixture().when(new RequestPurchase(ID, "a-1", BUYER, SELLER, 5000, "DKK", "Bike", "", 3))
                .thenExpectEvents(new OrderProposed("a-1", BUYER, SELLER, 5000, "DKK", "Bike", "", 3, NOW));
        Order agreed = fixture().given(new OrderProposed("a-1", BUYER, SELLER, 5000, "DKK", "Bike", "", 3, NOW.minusHours(1)))
                .when(new AcceptPurchase(ID, SELLER)).thenExpectEvents(agreed(NOW)).state();
        assertEquals(OrderStatus.AGREED, agreed.status());
        assertEquals(4100, agreed.payoutCents());
        assertEquals("not-seller", rejected(fixture().given(new OrderProposed("a-1", BUYER, SELLER, 5000, "DKK", "Bike", "", 3, NOW)).when(new AcceptPurchase(ID, BUYER))).getCode());
        assertEquals("own-announce", rejected(fixture().when(new RequestPurchase(ID, "a-1", SELLER, SELLER, 5000, "DKK", "Bike", "", 3))).getCode());
    }

    @Test
    void theAuctionDoorCreatesTheOrderAgreedAndTheCategoryRateApplies() {
        OrderPolicies withCategory = new OrderPolicies(OrderPolicies.FeeSchedule.of(8, 500, java.util.Map.of(42, new OrderPolicies.FeeSchedule.Rate(10, 0))),
                Duration.ofHours(48), Duration.ofDays(7), OrderPolicies.ReleaseOn.DELIVERY, Duration.ofDays(3), Duration.ofDays(14));
        Order o = fixture(withCategory).when(new CreateFromAuction(ID, "a-1", BUYER, SELLER, 12000, "DKK", "Bike", "", 42, "a-1"))
                .thenExpectEvents(new OrderAgreed("a-1", BUYER, SELLER, "AUCTION", "a-1", null, 12000, 1200, 10800, "DKK", "Bike", "", NOW)).state();
        assertEquals(Door.AUCTION, o.door());
    }

    @Test
    void paymentFactsAreIdempotentAndCancelStopsBeforePayment() {
        fixture().given(agreed(NOW)).when(new RecordPaymentConfirmed(ID, "p-1")).thenExpectEvents(paid(NOW));
        fixture().given(agreed(NOW), paid(NOW)).when(new RecordPaymentConfirmed(ID, "p-1")).thenExpectNoEvents();
        fixture().given(agreed(NOW), new OrderPaymentIntentCreated("p-1", NOW)).when(new Cancel(ID, BUYER, "USER", "no thanks"))
                .thenExpectEvents(new OrderCancelled(BUYER, "USER", "no thanks", NOW));
        assertEquals("already-paid", rejected(fixture().given(agreed(NOW), paid(NOW)).when(new Cancel(ID, BUYER, "USER", "x"))).getCode());
        // the pay window job: a no-op until the window is over
        fixture().given(agreed(NOW.minusHours(47))).when(new Cancel(ID, null, "SYSTEM", "pay window expired")).thenExpectNoEvents();
        fixture().given(agreed(NOW.minusHours(49))).when(new Cancel(ID, null, "SYSTEM", "pay window expired"))
                .thenExpectEvents(new OrderCancelled(null, "SYSTEM", "pay window expired", NOW));
    }

    @Test
    void theShipWindowExpiryAsksForARefund() {
        fixture().given(agreed(NOW.minusDays(8)), paid(NOW.minusDays(6))).when(new ExpireShipWindow(ID)).thenExpectNoEvents();
        fixture().given(agreed(NOW.minusDays(9)), paid(NOW.minusDays(8))).when(new ExpireShipWindow(ID))
                .thenExpectEvents(new RefundRequested(5000, "DKK", "ship window expired", NOW));
    }

    @Test
    void payoutIsRefusedWhileADisputeIsOpen() {
        assertEquals("dispute-open", rejected(fixture().given(agreed(NOW.minusDays(10)), paid(NOW.minusDays(9)), new OrderShipped("s-1", "T1", NOW.minusDays(8)),
                new OrderDelivered(NOW.minusDays(7)), new DisputeOpened(BUYER, "USER", "broken", NOW.minusDays(6))).when(new ReleasePayout(ID))).getCode());
        // staff resolve: RELEASE asks the payout at once, REFUND asks a refund
        fixture().given(agreed(NOW.minusDays(10)), paid(NOW.minusDays(9)), new OrderShipped("s-1", "T1", NOW.minusDays(8)), new OrderDelivered(NOW.minusDays(7)),
                new DisputeOpened(BUYER, "USER", "broken", NOW.minusDays(6))).when(new ResolveDispute(ID, 1, "REFUND", "photos show damage"))
                .thenExpectEvents(new DisputeResolved(1, "REFUND", "photos show damage", NOW), new RefundRequested(5000, "DKK", "dispute resolved: refund", NOW));
        fixture().given(agreed(NOW.minusDays(10)), paid(NOW.minusDays(9)), new OrderShipped("s-1", "T1", NOW.minusDays(8)), new OrderDelivered(NOW.minusDays(7)),
                new DisputeOpened(BUYER, "USER", "broken", NOW.minusDays(6))).when(new ResolveDispute(ID, 1, "RELEASE", "buyer withdrew"))
                .thenExpectEvents(new DisputeResolved(1, "RELEASE", "buyer withdrew", NOW), new PayoutReleaseRequested(4100, "DKK", NOW));
        assertEquals("not-disputable", rejected(fixture().given(agreed(NOW)).when(new OpenDispute(ID, BUYER, "USER", "x"))).getCode());
    }

    @Test
    void payoutIsRefusedBeforeCarrierDeliveryWhenReleaseOnDelivery() {
        assertEquals("not-delivered", rejected(fixture().given(agreed(NOW.minusDays(3)), paid(NOW.minusDays(2)), new OrderShipped("s-1", "T1", NOW.minusDays(1)))
                .when(new ReleasePayout(ID))).getCode());
        assertEquals("not-releasable", rejected(fixture().given(agreed(NOW.minusDays(3)), paid(NOW.minusDays(2))).when(new ReleasePayout(ID))).getCode());
        // delivered: a no-op during the grace, released after it
        fixture().given(agreed(NOW.minusDays(5)), paid(NOW.minusDays(4)), new OrderShipped("s-1", "T1", NOW.minusDays(3)), new OrderDelivered(NOW.minusDays(2)))
                .when(new ReleasePayout(ID)).thenExpectNoEvents();
        fixture().given(agreed(NOW.minusDays(6)), paid(NOW.minusDays(5)), new OrderShipped("s-1", "T1", NOW.minusDays(4)), new OrderDelivered(NOW.minusDays(3)))
                .when(new ReleasePayout(ID)).thenExpectEvents(new PayoutReleaseRequested(4100, "DKK", NOW));
        // the buyer's confirmation ends the grace early
        fixture().given(agreed(NOW.minusDays(5)), paid(NOW.minusDays(4)), new OrderShipped("s-1", "T1", NOW.minusDays(3)), new OrderDelivered(NOW.minusDays(2)))
                .when(new ConfirmReceipt(ID, BUYER)).thenExpectEvents(new ReceiptConfirmed(BUYER, NOW), new PayoutReleaseRequested(4100, "DKK", NOW));
        // no delivery event at all: released after the dispatch grace
        fixture().given(agreed(NOW.minusDays(16)), paid(NOW.minusDays(15)), new OrderShipped("s-1", "T1", NOW.minusDays(14)))
                .when(new ReleasePayout(ID)).thenExpectEvents(new PayoutReleaseRequested(4100, "DKK", NOW));
    }

    @Test
    void payoutIsAllowedAfterDispatchWhenReleaseOnDispatch() {
        OrderPolicies dispatch = POLICIES.withReleaseOn(OrderPolicies.ReleaseOn.DISPATCH);
        fixture(dispatch).given(agreed(NOW.minusDays(5)), paid(NOW.minusDays(4)), new OrderShipped("s-1", "T1", NOW.minusDays(3).minusHours(1)))
                .when(new ReleasePayout(ID)).thenExpectEvents(new PayoutReleaseRequested(4100, "DKK", NOW));
        fixture(dispatch).given(agreed(NOW.minusDays(3)), paid(NOW.minusDays(2)), new OrderShipped("s-1", "T1", NOW.minusDays(1)))
                .when(new ReleasePayout(ID)).thenExpectNoEvents();   // grace still running
    }

    @Test
    void windowsOutsideTheAllowedRangesAreRefused() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new OrderPolicies(POLICIES.fees(), Duration.ofHours(1),
                Duration.ofDays(7), OrderPolicies.ReleaseOn.DELIVERY, Duration.ofDays(3), Duration.ofDays(14)));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new OrderPolicies(POLICIES.fees(), Duration.ofHours(48),
                Duration.ofDays(8), OrderPolicies.ReleaseOn.DELIVERY, Duration.ofDays(3), Duration.ofDays(14)));
        assertEquals(100, POLICIES.fees().feeCents(null, 100));   // the fee is capped at the price
    }

    private static CommandRejectedException rejected(CommandFixture<Order>.Then then) {
        return then.thenExpectException(CommandRejectedException.class);
    }
}
