package org.linlinjava.announce.order.domain;

import com.opencqrs.framework.command.CommandHandler;
import com.opencqrs.framework.command.CommandHandlerDefinition;
import com.opencqrs.framework.command.StateRebuildingHandler;
import com.opencqrs.framework.command.StateRebuildingHandlerDefinition;
import org.linlinjava.announce.cqrs.command.Subjects;
import org.linlinjava.announce.order.domain.OrderCommands.AcceptPurchase;
import org.linlinjava.announce.order.domain.OrderCommands.Cancel;
import org.linlinjava.announce.order.domain.OrderCommands.ConfirmReceipt;
import org.linlinjava.announce.order.domain.OrderCommands.CreateFromAuction;
import org.linlinjava.announce.order.domain.OrderCommands.CreateFromOffer;
import org.linlinjava.announce.order.domain.OrderCommands.DeclinePurchase;
import org.linlinjava.announce.order.domain.OrderCommands.ExpireShipWindow;
import org.linlinjava.announce.order.domain.OrderCommands.OpenDispute;
import org.linlinjava.announce.order.domain.OrderCommands.RecordPaymentConfirmed;
import org.linlinjava.announce.order.domain.OrderCommands.RecordPaymentFailed;
import org.linlinjava.announce.order.domain.OrderCommands.RecordPaymentIntent;
import org.linlinjava.announce.order.domain.OrderCommands.RecordPayoutSent;
import org.linlinjava.announce.order.domain.OrderCommands.RecordRefunded;
import org.linlinjava.announce.order.domain.OrderCommands.RecordShipmentDelivered;
import org.linlinjava.announce.order.domain.OrderCommands.RecordShipmentDispatched;
import org.linlinjava.announce.order.domain.OrderCommands.ReleasePayout;
import org.linlinjava.announce.order.domain.OrderCommands.RequestPurchase;
import org.linlinjava.announce.order.domain.OrderCommands.ResolveDispute;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Command handling and state rebuilding of the Order aggregate as explicit OpenCQRS definitions. The {@code Record*}
 * handlers are idempotent (a fact already recorded returns the state unchanged), because the topics deliver at
 * least once. {@link #definitions(Clock, OrderPolicies)} is what tests hand to {@code CommandFixture.handlers(...)}.
 */
@Configuration(proxyBeanMethods = false)
public class OrderHandlers {

    public static final StateRebuildingHandlerDefinition<Order, OrderProposed> ON_PROPOSED = new StateRebuildingHandlerDefinition<>(
            Order.class, OrderProposed.class,
            (StateRebuildingHandler.FromObjectAndMetaDataAndSubject<Order, OrderProposed>) (s, e, m, subject) -> Order.apply(Subjects.idOf(subject), e));
    public static final StateRebuildingHandlerDefinition<Order, OrderAgreed> ON_AGREED = new StateRebuildingHandlerDefinition<>(
            Order.class, OrderAgreed.class,
            (StateRebuildingHandler.FromObjectAndMetaDataAndSubject<Order, OrderAgreed>) (s, e, m, subject) ->
                    s == null ? Order.apply(Subjects.idOf(subject), e) : s.apply(e));
    public static final StateRebuildingHandlerDefinition<Order, OrderDeclined> ON_DECLINED = def(OrderDeclined.class, Order::apply);
    public static final StateRebuildingHandlerDefinition<Order, OrderPaymentIntentCreated> ON_INTENT = def(OrderPaymentIntentCreated.class, Order::apply);
    public static final StateRebuildingHandlerDefinition<Order, OrderPaid> ON_PAID = def(OrderPaid.class, Order::apply);
    public static final StateRebuildingHandlerDefinition<Order, OrderPaymentFailed> ON_PAYMENT_FAILED = def(OrderPaymentFailed.class, Order::apply);
    public static final StateRebuildingHandlerDefinition<Order, OrderShipped> ON_SHIPPED = def(OrderShipped.class, Order::apply);
    public static final StateRebuildingHandlerDefinition<Order, OrderDelivered> ON_DELIVERED = def(OrderDelivered.class, Order::apply);
    public static final StateRebuildingHandlerDefinition<Order, ReceiptConfirmed> ON_RECEIPT = def(ReceiptConfirmed.class, Order::apply);
    public static final StateRebuildingHandlerDefinition<Order, DisputeOpened> ON_DISPUTE_OPENED = def(DisputeOpened.class, Order::apply);
    public static final StateRebuildingHandlerDefinition<Order, DisputeResolved> ON_DISPUTE_RESOLVED = def(DisputeResolved.class, Order::apply);
    public static final StateRebuildingHandlerDefinition<Order, PayoutReleaseRequested> ON_RELEASE = def(PayoutReleaseRequested.class, Order::apply);
    public static final StateRebuildingHandlerDefinition<Order, OrderSettled> ON_SETTLED = def(OrderSettled.class, Order::apply);
    public static final StateRebuildingHandlerDefinition<Order, OrderCancelled> ON_CANCELLED = def(OrderCancelled.class, Order::apply);
    public static final StateRebuildingHandlerDefinition<Order, RefundRequested> ON_REFUND_REQUESTED = def(RefundRequested.class, Order::apply);
    public static final StateRebuildingHandlerDefinition<Order, OrderRefunded> ON_REFUNDED = def(OrderRefunded.class, Order::apply);

    private static <E> StateRebuildingHandlerDefinition<Order, E> def(Class<E> type, StateRebuildingHandler.FromObject<Order, E> fn) {
        return new StateRebuildingHandlerDefinition<>(Order.class, type, fn);
    }

    // ------------------------------------------------------------------ the three doors

    public static CommandHandlerDefinition<Order, RequestPurchase, Order> requestPurchase(Clock clock) {
        return new CommandHandlerDefinition<>(Order.class, RequestPurchase.class,
                (CommandHandler.ForCommand<Order, RequestPurchase, Order>) (c, publisher) -> {
                    OrderRules.checkCreate(c.announceId(), c.buyerId(), c.sellerId(), c.priceCents());
                    OrderProposed e = new OrderProposed(c.announceId(), c.buyerId(), c.sellerId(), c.priceCents(), c.currency(), c.title(),
                            c.mainPicture(), c.categoryId(), now(clock));
                    publisher.publish(e);
                    return Order.apply(c.orderId(), e);
                });
    }

    public static CommandHandlerDefinition<Order, AcceptPurchase, Order> acceptPurchase(Clock clock, OrderPolicies policies) {
        return new CommandHandlerDefinition<>(Order.class, AcceptPurchase.class,
                (CommandHandler.ForInstanceAndCommand<Order, AcceptPurchase, Order>) (state, c, publisher) -> {
                    OrderRules.checkAccept(state, c.sellerId());
                    OrderAgreed e = agreed(state.announceId(), state.buyerId(), state.sellerId(), Door.FIXED_PRICE, null, null, state.priceCents(),
                            state.currency(), state.title(), state.mainPicture(), state.categoryId(), policies, now(clock));
                    publisher.publish(e);
                    return state.apply(e);
                });
    }

    public static CommandHandlerDefinition<Order, DeclinePurchase, Order> declinePurchase(Clock clock) {
        return new CommandHandlerDefinition<>(Order.class, DeclinePurchase.class,
                (CommandHandler.ForInstanceAndCommand<Order, DeclinePurchase, Order>) (state, c, publisher) -> {
                    OrderRules.checkAccept(state, c.sellerId());
                    OrderDeclined e = new OrderDeclined(c.sellerId(), c.reason(), now(clock));
                    publisher.publish(e);
                    return state.apply(e);
                });
    }

    public static CommandHandlerDefinition<Order, CreateFromAuction, Order> createFromAuction(Clock clock, OrderPolicies policies) {
        return new CommandHandlerDefinition<>(Order.class, CreateFromAuction.class,
                (CommandHandler.ForCommand<Order, CreateFromAuction, Order>) (c, publisher) -> {
                    OrderRules.checkCreate(c.announceId(), c.buyerId(), c.sellerId(), c.priceCents());
                    OrderAgreed e = agreed(c.announceId(), c.buyerId(), c.sellerId(), Door.AUCTION, c.auctionId(), null, c.priceCents(),
                            c.currency(), c.title(), c.mainPicture(), c.categoryId(), policies, now(clock));
                    publisher.publish(e);
                    return Order.apply(c.orderId(), e);
                });
    }

    public static CommandHandlerDefinition<Order, CreateFromOffer, Order> createFromOffer(Clock clock, OrderPolicies policies) {
        return new CommandHandlerDefinition<>(Order.class, CreateFromOffer.class,
                (CommandHandler.ForCommand<Order, CreateFromOffer, Order>) (c, publisher) -> {
                    OrderRules.checkCreate(c.announceId(), c.buyerId(), c.sellerId(), c.priceCents());
                    OrderAgreed e = agreed(c.announceId(), c.buyerId(), c.sellerId(), Door.OFFER, null, c.conversationId(), c.priceCents(),
                            c.currency(), c.title(), c.mainPicture(), c.categoryId(), policies, now(clock));
                    publisher.publish(e);
                    return Order.apply(c.orderId(), e);
                });
    }

    private static OrderAgreed agreed(String announceId, Integer buyerId, Integer sellerId, Door door, String auctionId, String conversationId,
                                      long priceCents, String currency, String title, String mainPicture, Integer categoryId,
                                      OrderPolicies policies, LocalDateTime now) {
        long fee = policies.fees().feeCents(categoryId, priceCents);
        return new OrderAgreed(announceId, buyerId, sellerId, door.name(), auctionId, conversationId, priceCents, fee, priceCents - fee,
                currency, title, mainPicture, now);
    }

    // ------------------------------------------------------------------ facts from payment and shipping (idempotent)

    public static CommandHandlerDefinition<Order, RecordPaymentIntent, Order> recordPaymentIntent(Clock clock) {
        return new CommandHandlerDefinition<>(Order.class, RecordPaymentIntent.class,
                (CommandHandler.ForInstanceAndCommand<Order, RecordPaymentIntent, Order>) (state, c, publisher) -> {
                    if (state.status() != OrderStatus.AGREED) {
                        return state;
                    }
                    OrderPaymentIntentCreated e = new OrderPaymentIntentCreated(c.paymentId(), now(clock));
                    publisher.publish(e);
                    return state.apply(e);
                });
    }

    public static CommandHandlerDefinition<Order, RecordPaymentConfirmed, Order> recordPaymentConfirmed(Clock clock) {
        return new CommandHandlerDefinition<>(Order.class, RecordPaymentConfirmed.class,
                (CommandHandler.ForInstanceAndCommand<Order, RecordPaymentConfirmed, Order>) (state, c, publisher) -> {
                    if (state.paidAt() != null) {
                        return state;
                    }
                    OrderRules.checkPaymentConfirmed(state);
                    OrderPaid e = new OrderPaid(c.paymentId(), now(clock));
                    publisher.publish(e);
                    return state.apply(e);
                });
    }

    public static CommandHandlerDefinition<Order, RecordPaymentFailed, Order> recordPaymentFailed(Clock clock) {
        return new CommandHandlerDefinition<>(Order.class, RecordPaymentFailed.class,
                (CommandHandler.ForInstanceAndCommand<Order, RecordPaymentFailed, Order>) (state, c, publisher) -> {
                    if (state.status() != OrderStatus.PAYMENT_PENDING && state.status() != OrderStatus.AGREED) {
                        return state;
                    }
                    OrderPaymentFailed e = new OrderPaymentFailed(c.reason(), now(clock));
                    publisher.publish(e);
                    return state.apply(e);
                });
    }

    public static CommandHandlerDefinition<Order, RecordShipmentDispatched, Order> recordShipmentDispatched(Clock clock) {
        return new CommandHandlerDefinition<>(Order.class, RecordShipmentDispatched.class,
                (CommandHandler.ForInstanceAndCommand<Order, RecordShipmentDispatched, Order>) (state, c, publisher) -> {
                    if (state.shippedAt() != null) {
                        return state;
                    }
                    OrderRules.checkShipped(state);
                    OrderShipped e = new OrderShipped(c.shipmentId(), c.trackingNumber(), now(clock));
                    publisher.publish(e);
                    return state.apply(e);
                });
    }

    public static CommandHandlerDefinition<Order, RecordShipmentDelivered, Order> recordShipmentDelivered(Clock clock) {
        return new CommandHandlerDefinition<>(Order.class, RecordShipmentDelivered.class,
                (CommandHandler.ForInstanceAndCommand<Order, RecordShipmentDelivered, Order>) (state, c, publisher) -> {
                    if (state.deliveredAt() != null) {
                        return state;
                    }
                    OrderRules.checkDelivered(state);
                    OrderDelivered e = new OrderDelivered(now(clock));
                    publisher.publish(e);
                    return state.apply(e);
                });
    }

    public static CommandHandlerDefinition<Order, RecordPayoutSent, Order> recordPayoutSent(Clock clock) {
        return new CommandHandlerDefinition<>(Order.class, RecordPayoutSent.class,
                (CommandHandler.ForInstanceAndCommand<Order, RecordPayoutSent, Order>) (state, c, publisher) -> {
                    if (state.status() == OrderStatus.SETTLED) {
                        return state;
                    }
                    if (state.status() != OrderStatus.RELEASING) {
                        throw new org.linlinjava.announce.cqrs.command.CommandRejectedException(org.springframework.http.HttpStatus.CONFLICT,
                                "not-releasing", "A payout was reported for an order in status " + state.status());
                    }
                    OrderSettled e = new OrderSettled(c.payoutRef(), now(clock));
                    publisher.publish(e);
                    return state.apply(e);
                });
    }

    public static CommandHandlerDefinition<Order, RecordRefunded, Order> recordRefunded(Clock clock) {
        return new CommandHandlerDefinition<>(Order.class, RecordRefunded.class,
                (CommandHandler.ForInstanceAndCommand<Order, RecordRefunded, Order>) (state, c, publisher) -> {
                    if (state.status() == OrderStatus.REFUNDED) {
                        return state;
                    }
                    OrderRefunded e = new OrderRefunded(c.refundRef(), now(clock));
                    publisher.publish(e);
                    return state.apply(e);
                });
    }

    // ------------------------------------------------------------------ buyer, seller, staff

    public static CommandHandlerDefinition<Order, ConfirmReceipt, Order> confirmReceipt(Clock clock, OrderPolicies policies) {
        return new CommandHandlerDefinition<>(Order.class, ConfirmReceipt.class,
                (CommandHandler.ForInstanceAndCommand<Order, ConfirmReceipt, Order>) (state, c, publisher) -> {
                    OrderRules.checkConfirmReceipt(state, c.buyerId());
                    LocalDateTime now = now(clock);
                    Order next = state;
                    if (state.receiptConfirmedAt() == null) {
                        ReceiptConfirmed confirmed = new ReceiptConfirmed(c.buyerId(), now);
                        publisher.publish(confirmed);
                        next = next.apply(confirmed);
                    }
                    if (OrderRules.checkRelease(next, policies, now) == OrderRules.Release.GO) {
                        PayoutReleaseRequested release = new PayoutReleaseRequested(next.payoutCents(), next.currency(), now);
                        publisher.publish(release);
                        next = next.apply(release);
                    }
                    return next;
                });
    }

    public static CommandHandlerDefinition<Order, OpenDispute, Order> openDispute(Clock clock) {
        return new CommandHandlerDefinition<>(Order.class, OpenDispute.class,
                (CommandHandler.ForInstanceAndCommand<Order, OpenDispute, Order>) (state, c, publisher) -> {
                    if ("SYSTEM".equals(c.openedByType()) && state.status() == OrderStatus.DISPUTED) {
                        return state;
                    }
                    OrderRules.checkOpenDispute(state, c);
                    DisputeOpened e = new DisputeOpened(c.openedBy(), c.openedByType(), c.reason(), now(clock));
                    publisher.publish(e);
                    return state.apply(e);
                });
    }

    /** RELEASE asks the payout immediately (the dispute replaced the grace); REFUND asks the payment context for a refund. */
    public static CommandHandlerDefinition<Order, ResolveDispute, Order> resolveDispute(Clock clock) {
        return new CommandHandlerDefinition<>(Order.class, ResolveDispute.class,
                (CommandHandler.ForInstanceAndCommand<Order, ResolveDispute, Order>) (state, c, publisher) -> {
                    OrderRules.checkResolveDispute(state, c);
                    LocalDateTime now = now(clock);
                    DisputeResolved resolved = new DisputeResolved(c.adminId(), c.resolution(), c.note(), now);
                    publisher.publish(resolved);
                    Order next = state.apply(resolved);
                    if ("REFUND".equals(c.resolution())) {
                        RefundRequested refund = new RefundRequested(next.priceCents(), next.currency(), "dispute resolved: refund", now);
                        publisher.publish(refund);
                        return next.apply(refund);
                    }
                    PayoutReleaseRequested release = new PayoutReleaseRequested(next.payoutCents(), next.currency(), now);
                    publisher.publish(release);
                    return next.apply(release);
                });
    }

    /** The release rule; a no-op while the grace runs, rejected on a hard block. */
    public static CommandHandlerDefinition<Order, ReleasePayout, Order> releasePayout(Clock clock, OrderPolicies policies) {
        return new CommandHandlerDefinition<>(Order.class, ReleasePayout.class,
                (CommandHandler.ForInstanceAndCommand<Order, ReleasePayout, Order>) (state, c, publisher) -> {
                    LocalDateTime now = now(clock);
                    if (OrderRules.checkRelease(state, policies, now) != OrderRules.Release.GO) {
                        return state;
                    }
                    PayoutReleaseRequested e = new PayoutReleaseRequested(state.payoutCents(), state.currency(), now);
                    publisher.publish(e);
                    return state.apply(e);
                });
    }

    public static CommandHandlerDefinition<Order, Cancel, Order> cancel(Clock clock, OrderPolicies policies) {
        return new CommandHandlerDefinition<>(Order.class, Cancel.class,
                (CommandHandler.ForInstanceAndCommand<Order, Cancel, Order>) (state, c, publisher) -> {
                    LocalDateTime now = now(clock);
                    if ("SYSTEM".equals(c.actorType())) {
                        if (!OrderRules.isPayWindowOver(state, policies, now)) {
                            return state;
                        }
                    } else {
                        OrderRules.checkCancel(state, c);
                    }
                    OrderCancelled e = new OrderCancelled(c.actorId(), c.actorType(), c.reason(), now);
                    publisher.publish(e);
                    return state.apply(e);
                });
    }

    public static CommandHandlerDefinition<Order, ExpireShipWindow, Order> expireShipWindow(Clock clock, OrderPolicies policies) {
        return new CommandHandlerDefinition<>(Order.class, ExpireShipWindow.class,
                (CommandHandler.ForInstanceAndCommand<Order, ExpireShipWindow, Order>) (state, c, publisher) -> {
                    LocalDateTime now = now(clock);
                    if (!OrderRules.isShipWindowOver(state, policies, now)) {
                        return state;
                    }
                    RefundRequested e = new RefundRequested(state.priceCents(), state.currency(), "ship window expired", now);
                    publisher.publish(e);
                    return state.apply(e);
                });
    }

    /** All definitions, for {@code CommandFixture.handlers(...)}. */
    public static List<Object> definitions(Clock clock, OrderPolicies policies) {
        return List.of(ON_PROPOSED, ON_AGREED, ON_DECLINED, ON_INTENT, ON_PAID, ON_PAYMENT_FAILED, ON_SHIPPED, ON_DELIVERED, ON_RECEIPT,
                ON_DISPUTE_OPENED, ON_DISPUTE_RESOLVED, ON_RELEASE, ON_SETTLED, ON_CANCELLED, ON_REFUND_REQUESTED, ON_REFUNDED,
                requestPurchase(clock), acceptPurchase(clock, policies), declinePurchase(clock), createFromAuction(clock, policies),
                createFromOffer(clock, policies), recordPaymentIntent(clock), recordPaymentConfirmed(clock), recordPaymentFailed(clock),
                recordShipmentDispatched(clock), recordShipmentDelivered(clock), recordPayoutSent(clock), recordRefunded(clock),
                confirmReceipt(clock, policies), openDispute(clock), resolveDispute(clock), releasePayout(clock, policies),
                cancel(clock, policies), expireShipWindow(clock, policies));
    }

    private static LocalDateTime now(Clock clock) {
        return LocalDateTime.now(clock);
    }

    // ------------------------------------------------------------------ beans

    @Bean StateRebuildingHandlerDefinition<Order, OrderProposed> orderOnProposed() { return ON_PROPOSED; }
    @Bean StateRebuildingHandlerDefinition<Order, OrderAgreed> orderOnAgreed() { return ON_AGREED; }
    @Bean StateRebuildingHandlerDefinition<Order, OrderDeclined> orderOnDeclined() { return ON_DECLINED; }
    @Bean StateRebuildingHandlerDefinition<Order, OrderPaymentIntentCreated> orderOnIntent() { return ON_INTENT; }
    @Bean StateRebuildingHandlerDefinition<Order, OrderPaid> orderOnPaid() { return ON_PAID; }
    @Bean StateRebuildingHandlerDefinition<Order, OrderPaymentFailed> orderOnPaymentFailed() { return ON_PAYMENT_FAILED; }
    @Bean StateRebuildingHandlerDefinition<Order, OrderShipped> orderOnShipped() { return ON_SHIPPED; }
    @Bean StateRebuildingHandlerDefinition<Order, OrderDelivered> orderOnDelivered() { return ON_DELIVERED; }
    @Bean StateRebuildingHandlerDefinition<Order, ReceiptConfirmed> orderOnReceipt() { return ON_RECEIPT; }
    @Bean StateRebuildingHandlerDefinition<Order, DisputeOpened> orderOnDisputeOpened() { return ON_DISPUTE_OPENED; }
    @Bean StateRebuildingHandlerDefinition<Order, DisputeResolved> orderOnDisputeResolved() { return ON_DISPUTE_RESOLVED; }
    @Bean StateRebuildingHandlerDefinition<Order, PayoutReleaseRequested> orderOnRelease() { return ON_RELEASE; }
    @Bean StateRebuildingHandlerDefinition<Order, OrderSettled> orderOnSettled() { return ON_SETTLED; }
    @Bean StateRebuildingHandlerDefinition<Order, OrderCancelled> orderOnCancelled() { return ON_CANCELLED; }
    @Bean StateRebuildingHandlerDefinition<Order, RefundRequested> orderOnRefundRequested() { return ON_REFUND_REQUESTED; }
    @Bean StateRebuildingHandlerDefinition<Order, OrderRefunded> orderOnRefunded() { return ON_REFUNDED; }
    @Bean CommandHandlerDefinition<Order, RequestPurchase, Order> orderRequestPurchase(Clock clock) { return requestPurchase(clock); }
    @Bean CommandHandlerDefinition<Order, AcceptPurchase, Order> orderAcceptPurchase(Clock clock, OrderPolicies p) { return acceptPurchase(clock, p); }
    @Bean CommandHandlerDefinition<Order, DeclinePurchase, Order> orderDeclinePurchase(Clock clock) { return declinePurchase(clock); }
    @Bean CommandHandlerDefinition<Order, CreateFromAuction, Order> orderCreateFromAuction(Clock clock, OrderPolicies p) { return createFromAuction(clock, p); }
    @Bean CommandHandlerDefinition<Order, CreateFromOffer, Order> orderCreateFromOffer(Clock clock, OrderPolicies p) { return createFromOffer(clock, p); }
    @Bean CommandHandlerDefinition<Order, RecordPaymentIntent, Order> orderRecordPaymentIntent(Clock clock) { return recordPaymentIntent(clock); }
    @Bean CommandHandlerDefinition<Order, RecordPaymentConfirmed, Order> orderRecordPaymentConfirmed(Clock clock) { return recordPaymentConfirmed(clock); }
    @Bean CommandHandlerDefinition<Order, RecordPaymentFailed, Order> orderRecordPaymentFailed(Clock clock) { return recordPaymentFailed(clock); }
    @Bean CommandHandlerDefinition<Order, RecordShipmentDispatched, Order> orderRecordShipmentDispatched(Clock clock) { return recordShipmentDispatched(clock); }
    @Bean CommandHandlerDefinition<Order, RecordShipmentDelivered, Order> orderRecordShipmentDelivered(Clock clock) { return recordShipmentDelivered(clock); }
    @Bean CommandHandlerDefinition<Order, RecordPayoutSent, Order> orderRecordPayoutSent(Clock clock) { return recordPayoutSent(clock); }
    @Bean CommandHandlerDefinition<Order, RecordRefunded, Order> orderRecordRefunded(Clock clock) { return recordRefunded(clock); }
    @Bean CommandHandlerDefinition<Order, ConfirmReceipt, Order> orderConfirmReceipt(Clock clock, OrderPolicies p) { return confirmReceipt(clock, p); }
    @Bean CommandHandlerDefinition<Order, OpenDispute, Order> orderOpenDispute(Clock clock) { return openDispute(clock); }
    @Bean CommandHandlerDefinition<Order, ResolveDispute, Order> orderResolveDispute(Clock clock) { return resolveDispute(clock); }
    @Bean CommandHandlerDefinition<Order, ReleasePayout, Order> orderReleasePayout(Clock clock, OrderPolicies p) { return releasePayout(clock, p); }
    @Bean CommandHandlerDefinition<Order, Cancel, Order> orderCancel(Clock clock, OrderPolicies p) { return cancel(clock, p); }
    @Bean CommandHandlerDefinition<Order, ExpireShipWindow, Order> orderExpireShipWindow(Clock clock, OrderPolicies p) { return expireShipWindow(clock, p); }
}
