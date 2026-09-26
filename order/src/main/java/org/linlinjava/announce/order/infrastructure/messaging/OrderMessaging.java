package org.linlinjava.announce.order.infrastructure.messaging;

import com.opencqrs.framework.command.CommandSubjectAlreadyExistsException;
import com.opencqrs.framework.command.CommandSubjectDoesNotExistException;
import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.cqrs.command.Subjects;
import org.linlinjava.announce.cqrs.messaging.PublicEventRelay;
import org.linlinjava.announce.cqrs.messaging.PublicEventRelays;
import org.linlinjava.announce.cqrs.messaging.PublicEventSubscriber;
import org.linlinjava.announce.cqrs.messaging.PublicEventSubscribers;
import org.linlinjava.announce.db.dao.reactive.AnnounceOrderMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceOrderSellerMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceOrderSnapshotMapper;
import org.linlinjava.announce.db.domain.AnnounceOrder;
import org.linlinjava.announce.db.domain.AnnounceOrderSeller;
import org.linlinjava.announce.db.domain.AnnounceOrderSnapshot;
import org.linlinjava.announce.goods.api.AnnounceEditedV1;
import org.linlinjava.announce.goods.api.AnnounceExpiredV1;
import org.linlinjava.announce.goods.api.AnnouncePublishedV1;
import org.linlinjava.announce.goods.api.AnnounceSoldV1;
import org.linlinjava.announce.goods.api.AnnounceWithdrawnV1;
import org.linlinjava.announce.messaging.api.OfferAcceptedV1;
import org.linlinjava.announce.order.api.AuctionClosedWonV1;
import org.linlinjava.announce.order.api.AuctionSnaggedV1;
import org.linlinjava.announce.order.api.OrderAgreedV1;
import org.linlinjava.announce.order.api.OrderCancelledV1;
import org.linlinjava.announce.order.api.OrderDeliveredV1;
import org.linlinjava.announce.order.api.OrderPaidV1;
import org.linlinjava.announce.order.api.OrderRefundedV1;
import org.linlinjava.announce.order.api.OrderSettledV1;
import org.linlinjava.announce.order.api.OrderShippedV1;
import org.linlinjava.announce.order.api.PaymentConfirmedV1;
import org.linlinjava.announce.order.api.PaymentFailedV1;
import org.linlinjava.announce.order.api.PaymentIntentCreatedV1;
import org.linlinjava.announce.order.api.PayoutReleaseRequestedV1;
import org.linlinjava.announce.order.api.PayoutSentV1;
import org.linlinjava.announce.order.api.RefundRequestedV1;
import org.linlinjava.announce.order.api.RefundSentV1;
import org.linlinjava.announce.order.api.SellerAccountUpdatedV1;
import org.linlinjava.announce.order.api.ShipmentDeliveredV1;
import org.linlinjava.announce.order.api.ShipmentDispatchedV1;
import org.linlinjava.announce.order.api.ShipmentFailedV1;
import org.linlinjava.announce.order.application.OrderService;
import org.linlinjava.announce.order.domain.OrderCommands.OpenDispute;
import org.linlinjava.announce.order.domain.OrderCommands.RecordPaymentConfirmed;
import org.linlinjava.announce.order.domain.OrderCommands.RecordPaymentFailed;
import org.linlinjava.announce.order.domain.OrderCommands.RecordPaymentIntent;
import org.linlinjava.announce.order.domain.OrderCommands.RecordPayoutSent;
import org.linlinjava.announce.order.domain.OrderCommands.RecordRefunded;
import org.linlinjava.announce.order.domain.OrderCommands.RecordShipmentDelivered;
import org.linlinjava.announce.order.domain.OrderCommands.RecordShipmentDispatched;
import org.linlinjava.announce.order.domain.events.OrderAgreed;
import org.linlinjava.announce.order.domain.events.OrderCancelled;
import org.linlinjava.announce.order.domain.events.OrderDelivered;
import org.linlinjava.announce.order.domain.events.OrderPaid;
import org.linlinjava.announce.order.domain.events.OrderRefunded;
import org.linlinjava.announce.order.domain.events.OrderSettled;
import org.linlinjava.announce.order.domain.events.OrderShipped;
import org.linlinjava.announce.order.domain.events.PayoutReleaseRequested;
import org.linlinjava.announce.order.domain.events.RefundRequested;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.function.Consumer;

/**
 * The order context over Kafka ({@code doc/order-context.md} §7). <b>Out:</b> relay {@code order-public} on
 * {@code order-out-0} ({@code order.v1}); the records need buyer, seller and announce, which the mappings read from
 * the read model. <b>In:</b> five subscribers, one per topic: {@code announce.v1} (snapshot upsert), {@code auction.v1}
 * (the auction door), {@code messaging.v1} (the offer door), {@code payment.v1} (payment facts, seller gate),
 * {@code shipping.v1} (carrier facts). A fact that contradicts the deal's state is logged and skipped, never retried
 * forever: the record's event id is the command id, so a redelivery replays the stored result.
 */
@Configuration(proxyBeanMethods = false)
public class OrderMessaging {

    public static final String RELAY_GROUP = "order-public";
    public static final String OUT_BINDING = "order-out-0";

    private static final Logger log = LoggerFactory.getLogger(OrderMessaging.class);

    @Bean
    PublicEventRelay orderPublicEvents(PublicEventRelays relays, AnnounceOrderMapper orders) {
        return relays.forGroup(RELAY_GROUP).binding(OUT_BINDING)
                .map(OrderAgreed.class, OrderAgreedV1.class, (e, env) -> new OrderAgreedV1(id(env), e.announceId(), e.buyerId(), e.sellerId(),
                        e.door(), e.priceCents(), e.feeCents(), e.payoutCents(), e.currency(), e.title(), e.at()))
                .map(OrderPaid.class, OrderPaidV1.class, (e, env) -> {
                    AnnounceOrder o = row(orders, env);
                    return new OrderPaidV1(id(env), o.getAnnounceId(), o.getBuyerId(), o.getSellerId(), cents(o.getAgreedPrice()), o.getCurrency(), e.at());
                })
                .map(OrderShipped.class, OrderShippedV1.class, (e, env) -> {
                    AnnounceOrder o = row(orders, env);
                    return new OrderShippedV1(id(env), o.getAnnounceId(), o.getBuyerId(), o.getSellerId(), e.shipmentId(), e.trackingNumber(), e.at());
                })
                .map(OrderDelivered.class, OrderDeliveredV1.class, (e, env) -> {
                    AnnounceOrder o = row(orders, env);
                    return new OrderDeliveredV1(id(env), o.getAnnounceId(), o.getBuyerId(), o.getSellerId(), e.at());
                })
                .map(OrderSettled.class, OrderSettledV1.class, (e, env) -> {
                    AnnounceOrder o = row(orders, env);
                    return new OrderSettledV1(id(env), o.getAnnounceId(), o.getBuyerId(), o.getSellerId(), cents(o.getPayoutAmount()), o.getCurrency(), e.at());
                })
                .map(OrderCancelled.class, OrderCancelledV1.class, (e, env) -> {
                    AnnounceOrder o = row(orders, env);
                    return new OrderCancelledV1(id(env), o.getAnnounceId(), o.getBuyerId(), o.getSellerId(), e.reason(), e.at());
                })
                .map(OrderRefunded.class, OrderRefundedV1.class, (e, env) -> {
                    AnnounceOrder o = row(orders, env);
                    return new OrderRefundedV1(id(env), o.getAnnounceId(), o.getBuyerId(), o.getSellerId(), cents(o.getAgreedPrice()), o.getCurrency(), "refunded", e.at());
                })
                .map(PayoutReleaseRequested.class, PayoutReleaseRequestedV1.class, (e, env) ->
                        new PayoutReleaseRequestedV1(id(env), row(orders, env).getSellerId(), e.payoutCents(), e.currency(), e.at()))
                .map(RefundRequested.class, RefundRequestedV1.class, (e, env) ->
                        new RefundRequestedV1(id(env), row(orders, env).getBuyerId(), e.amountCents(), e.currency(), e.reason(), e.at()))
                .build();
    }

    /** The ACL: what this context knows about an announce, from announce.v1. Edits update only rows already known. */
    @Bean
    Consumer<Message<byte[]>> orderAnnounceEvents(PublicEventSubscribers subscribers, AnnounceOrderSnapshotMapper snapshots) {
        return subscribers.forTopic("announce")
                .on(AnnouncePublishedV1.class, (e, h) -> {
                    AnnounceOrderSnapshot row = new AnnounceOrderSnapshot();
                    row.setId(e.announceId());
                    row.setAnnounceId(e.announceId());
                    row.setKind(e.kind());
                    row.setSellerId(e.sellerId());
                    row.setTitle(e.title());
                    row.setMainPicture(e.mainPicture());
                    row.setPrice(BigDecimal.valueOf(e.priceCents(), 2));
                    row.setCurrency(e.currency());
                    row.setSaleType(e.saleType());
                    row.setCategoryId(e.categoryId());
                    row.setStatus("PUBLISHED");
                    row.setPublishedAt(e.publishedAt());
                    row.setExpiresAt(e.expiresAt());
                    row.setLastEventId(h.eventId().orElse(null));
                    row.setUpdateTime(e.publishedAt());
                    return snapshots.selectByPrimaryKey(e.announceId()).hasElement()
                            .flatMap(exists -> exists ? snapshots.updateByPrimaryKeySelective(row) : snapshots.insertSelective(row)).then();
                })
                .on(AnnounceEditedV1.class, (e, h) -> {
                    AnnounceOrderSnapshot row = new AnnounceOrderSnapshot();
                    row.setId(e.announceId());
                    row.setTitle(e.title());
                    row.setMainPicture(e.mainPicture());
                    row.setPrice(BigDecimal.valueOf(e.priceCents(), 2));
                    row.setCurrency(e.currency());
                    row.setSaleType(e.saleType());
                    row.setCategoryId(e.categoryId());
                    row.setLastEventId(h.eventId().orElse(null));
                    return snapshots.updateByPrimaryKeySelective(row).then();
                })
                .on(AnnounceWithdrawnV1.class, (e, h) -> snapshotStatus(snapshots, e.announceId(), "WITHDRAWN", h))
                .on(AnnounceExpiredV1.class, (e, h) -> snapshotStatus(snapshots, e.announceId(), "EXPIRED", h))
                .on(AnnounceSoldV1.class, (e, h) -> snapshotStatus(snapshots, e.announceId(), "SOLD", h))
                .build();
    }

    /** The auction door. */
    @Bean
    Consumer<Message<byte[]>> orderAuctionEvents(PublicEventSubscribers subscribers, OrderService orders) {
        return subscribers.forTopic("auction")
                .on(AuctionClosedWonV1.class, (e, h) -> orders.createFromAuction(e.announceId(), e.winnerId(), e.sellerId(), e.priceCents(), e.currency(), commandId("won", h))
                        .then().onErrorResume(CommandSubjectAlreadyExistsException.class, x -> Mono.empty()))
                .on(AuctionSnaggedV1.class, (e, h) -> orders.createFromAuction(e.announceId(), e.buyerId(), e.sellerId(), e.priceCents(), e.currency(), commandId("snag", h))
                        .then().onErrorResume(CommandSubjectAlreadyExistsException.class, x -> Mono.empty()))
                .build();
    }

    /** The chat-offer door (contract of doc/order-context.md §9). */
    @Bean
    Consumer<Message<byte[]>> orderMessagingEvents(PublicEventSubscribers subscribers, OrderService orders) {
        return subscribers.forTopic("messaging")
                .on(OfferAcceptedV1.class, (e, h) -> orders.createFromOffer(e.conversationId(), e.announceId(), e.buyerId(), e.sellerId(),
                                e.amountCents(), e.currency(), commandId("offer", h))
                        .then().onErrorResume(CommandSubjectAlreadyExistsException.class, x -> Mono.empty()))
                .build();
    }

    /** Facts from the payment context, and the seller gate. */
    @Bean
    Consumer<Message<byte[]>> orderPaymentEvents(PublicEventSubscribers subscribers, OrderService orders, AnnounceOrderSellerMapper sellers) {
        return subscribers.forTopic("payment")
                .on(PaymentIntentCreatedV1.class, (e, h) -> fact(orders.send(new RecordPaymentIntent(e.orderId(), e.paymentId()), commandId("intent", h)), e))
                .on(PaymentConfirmedV1.class, (e, h) -> fact(orders.send(new RecordPaymentConfirmed(e.orderId(), e.paymentId()), commandId("confirmed", h)), e))
                .on(PaymentFailedV1.class, (e, h) -> fact(orders.send(new RecordPaymentFailed(e.orderId(), e.reason()), commandId("failed", h)), e))
                .on(PayoutSentV1.class, (e, h) -> fact(orders.send(new RecordPayoutSent(e.orderId(), e.payoutRef()), commandId("payout", h)), e))
                .on(RefundSentV1.class, (e, h) -> fact(orders.send(new RecordRefunded(e.orderId(), e.refundRef()), commandId("refund", h)), e))
                .on(SellerAccountUpdatedV1.class, (e, h) -> {
                    AnnounceOrderSeller row = new AnnounceOrderSeller();
                    row.setId(e.userId());
                    row.setPayoutsEnabled(e.payoutsEnabled());
                    row.setAccountStatus(e.status());
                    row.setLastEventId(h.eventId().orElse(null));
                    row.setUpdateTime(e.at());
                    return sellers.selectByPrimaryKey(e.userId()).hasElement()
                            .flatMap(exists -> exists ? sellers.updateByPrimaryKeySelective(row) : sellers.insertSelective(row)).then();
                })
                .build();
    }

    /** Facts from the shipping context; a failed parcel opens a dispute for staff. */
    @Bean
    Consumer<Message<byte[]>> orderShippingEvents(PublicEventSubscribers subscribers, OrderService orders) {
        return subscribers.forTopic("shipping")
                .on(ShipmentDispatchedV1.class, (e, h) -> fact(orders.send(new RecordShipmentDispatched(e.orderId(), e.orderId(), e.trackingNumber()), commandId("dispatched", h)), e))
                .on(ShipmentDeliveredV1.class, (e, h) -> fact(orders.send(new RecordShipmentDelivered(e.orderId()), commandId("delivered", h)), e))
                .on(ShipmentFailedV1.class, (e, h) -> fact(orders.send(new OpenDispute(e.orderId(), null, "SYSTEM", "carrier: " + e.reason()), commandId("shipment-failed", h)), e))
                .build();
    }

    // ------------------------------------------------------------------ helpers

    private static Mono<Void> fact(Mono<?> dispatch, Object event) {
        return dispatch.then().onErrorResume(t -> t instanceof CommandRejectedException || t instanceof CommandSubjectDoesNotExistException, t -> {
            log.error("fact {} does not fit the deal and was skipped: {}", event, t.getMessage());
            return Mono.empty();
        });
    }

    private static Mono<Void> snapshotStatus(AnnounceOrderSnapshotMapper snapshots, String announceId, String status, PublicEventSubscriber.Headers h) {
        AnnounceOrderSnapshot row = new AnnounceOrderSnapshot();
        row.setId(announceId);
        row.setStatus(status);
        row.setLastEventId(h.eventId().orElse(null));
        return snapshots.updateByPrimaryKeySelective(row).then();
    }

    private static String commandId(String action, PublicEventSubscriber.Headers headers) {
        return headers.eventId().map(id -> action + ":" + id).orElse(null);
    }

    private static String id(org.linlinjava.announce.cqrs.readside.EventEnvelope env) {
        return Subjects.idOf(env.subject());
    }

    /** The relay maps on the processor thread, never the event loop, so a read-model lookup may block here; a missing row makes the framework retry. */
    private static AnnounceOrder row(AnnounceOrderMapper orders, org.linlinjava.announce.cqrs.readside.EventEnvelope env) {
        AnnounceOrder o = orders.selectByPrimaryKey(id(env)).block();
        if (o == null) {
            throw new IllegalStateException("order " + id(env) + " not in the read model yet");
        }
        return o;
    }

    private static long cents(BigDecimal amount) {
        return amount == null ? 0L : amount.movePointRight(2).longValueExact();
    }
}
