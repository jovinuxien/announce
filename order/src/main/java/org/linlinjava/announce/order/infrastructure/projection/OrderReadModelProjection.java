package org.linlinjava.announce.order.infrastructure.projection;

import org.linlinjava.announce.cqrs.command.Subjects;
import org.linlinjava.announce.cqrs.readside.EventEnvelope;
import org.linlinjava.announce.cqrs.readside.ProjectionHandlers;
import org.linlinjava.announce.cqrs.readside.ReactiveProjection;
import org.linlinjava.announce.db.dao.reactive.AnnounceOrderMapper;
import org.linlinjava.announce.db.domain.AnnounceOrder;
import org.linlinjava.announce.order.domain.events.DisputeOpened;
import org.linlinjava.announce.order.domain.events.DisputeResolved;
import org.linlinjava.announce.order.domain.events.OrderAgreed;
import org.linlinjava.announce.order.domain.events.OrderCancelled;
import org.linlinjava.announce.order.domain.events.OrderDeclined;
import org.linlinjava.announce.order.domain.events.OrderDelivered;
import org.linlinjava.announce.order.domain.events.OrderPaid;
import org.linlinjava.announce.order.domain.events.OrderPaymentIntentCreated;
import org.linlinjava.announce.order.domain.events.OrderProposed;
import org.linlinjava.announce.order.domain.events.OrderRefunded;
import org.linlinjava.announce.order.domain.events.OrderSettled;
import org.linlinjava.announce.order.domain.events.OrderShipped;
import org.linlinjava.announce.order.domain.events.PayoutReleaseRequested;
import org.linlinjava.announce.order.domain.events.ReceiptConfirmed;
import org.linlinjava.announce.order.domain.events.RefundRequested;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Group {@code order-read-model}: keeps {@code announce_order} in step with the aggregate; upsert by id, at-least-once. */
@Component
public class OrderReadModelProjection implements ReactiveProjection {

    public static final String GROUP = "order-read-model";

    private final AnnounceOrderMapper orders;
    private final DatabaseClient db;

    public OrderReadModelProjection(AnnounceOrderMapper orders, DatabaseClient db) {
        this.orders = orders;
        this.db = db;
    }

    @Override
    public String group() {
        return GROUP;
    }

    @Override
    public void configure(ProjectionHandlers handlers) {
        handlers.on(OrderProposed.class, (e, env) -> {
            AnnounceOrder row = base(env, e.at());
            row.setAnnounceId(e.announceId());
            row.setBuyerId(e.buyerId());
            row.setSellerId(e.sellerId());
            row.setDoor("FIXED_PRICE");
            row.setTitle(e.title());
            row.setMainPicture(e.mainPicture());
            row.setAgreedPrice(money(e.priceCents()));
            row.setCurrency(e.currency());
            row.setStatus("PROPOSED");
            row.setAddTime(e.at());
            row.setDeleted(false);
            return upsert(row);
        });
        handlers.on(OrderAgreed.class, (e, env) -> {
            AnnounceOrder row = base(env, e.at());
            row.setAnnounceId(e.announceId());
            row.setBuyerId(e.buyerId());
            row.setSellerId(e.sellerId());
            row.setDoor(e.door());
            row.setAuctionId(e.auctionId());
            row.setConversationId(e.conversationId());
            row.setTitle(e.title());
            row.setMainPicture(e.mainPicture());
            row.setAgreedPrice(money(e.priceCents()));
            row.setFeeAmount(money(e.feeCents()));
            row.setPayoutAmount(money(e.payoutCents()));
            row.setCurrency(e.currency());
            row.setStatus("AGREED");
            row.setAgreedAt(e.at());
            row.setAddTime(e.at());
            row.setDeleted(false);
            return upsert(row);
        });
        handlers.on(OrderDeclined.class, (e, env) -> status(env, "DECLINED", e.at(), r -> r.setClosedAt(e.at())));
        handlers.on(OrderPaymentIntentCreated.class, (e, env) -> status(env, "PAYMENT_PENDING", e.at(), r -> r.setPaymentId(e.paymentId())));
        handlers.on(OrderPaid.class, (e, env) -> status(env, "PAID", e.at(), r -> { r.setPaymentId(e.paymentId()); r.setPaidAt(e.at()); }));
        handlers.on(OrderShipped.class, (e, env) -> status(env, "SHIPPED", e.at(), r -> { r.setShipmentId(e.shipmentId()); r.setShippedAt(e.at()); }));
        handlers.on(OrderDelivered.class, (e, env) -> orders.selectByPrimaryKey(idOf(env)).flatMap(current ->
                status(env, "DISPUTED".equals(current.getStatus()) ? "DISPUTED" : "DELIVERED", e.at(), r -> r.setDeliveredAt(e.at()))));
        handlers.on(ReceiptConfirmed.class, (e, env) -> status(env, null, e.at(), r -> r.setReceiptConfirmedAt(e.at())));
        handlers.on(DisputeOpened.class, (e, env) -> status(env, "DISPUTED", e.at(), r -> { r.setDisputeReason(e.reason()); r.setDisputeOpenedAt(e.at()); }));
        handlers.on(DisputeResolved.class, (e, env) -> status(env, "REFUND".equals(e.resolution()) ? "REFUNDING" : "RELEASING", e.at(), r -> { }));
        handlers.on(PayoutReleaseRequested.class, (e, env) -> status(env, "RELEASING", e.at(), r -> r.setReleasedAt(e.at())));
        handlers.on(OrderSettled.class, (e, env) -> status(env, "SETTLED", e.at(), r -> r.setSettledAt(e.at())));
        handlers.on(OrderCancelled.class, (e, env) -> status(env, "CANCELLED", e.at(), r -> r.setClosedAt(e.at())));
        handlers.on(RefundRequested.class, (e, env) -> status(env, "REFUNDING", e.at(), r -> { }));
        handlers.on(OrderRefunded.class, (e, env) -> status(env, "REFUNDED", e.at(), r -> r.setClosedAt(e.at())));
    }

    @Override
    public Mono<Void> reset() {
        return db.sql("delete from announce_order").then();
    }

    private interface Patch { void apply(AnnounceOrder row); }

    private Mono<Void> status(EventEnvelope env, String status, LocalDateTime at, Patch patch) {
        AnnounceOrder row = base(env, at);
        row.setStatus(status);
        patch.apply(row);
        return orders.updateByPrimaryKeySelective(row).then();
    }

    private Mono<Void> upsert(AnnounceOrder row) {
        return orders.selectByPrimaryKey(row.getId()).hasElement()
                .flatMap(exists -> exists ? orders.updateByPrimaryKeySelective(row) : orders.insertSelective(row)).then();
    }

    private static AnnounceOrder base(EventEnvelope env, LocalDateTime at) {
        AnnounceOrder row = new AnnounceOrder();
        row.setId(idOf(env));
        row.setLastEventId(env.eventId());
        row.setUpdateTime(at);
        return row;
    }

    private static String idOf(EventEnvelope env) {
        return Subjects.idOf(env.subject());
    }

    private static BigDecimal money(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }
}
