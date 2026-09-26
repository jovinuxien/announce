package org.linlinjava.announce.shipping.infrastructure.projection;

import org.linlinjava.announce.cqrs.command.Subjects;
import org.linlinjava.announce.cqrs.readside.EventEnvelope;
import org.linlinjava.announce.cqrs.readside.ProjectionHandlers;
import org.linlinjava.announce.cqrs.readside.ReactiveProjection;
import org.linlinjava.announce.db.dao.reactive.AnnounceShipmentMapper;
import org.linlinjava.announce.db.domain.AnnounceShipment;
import org.linlinjava.announce.shipping.domain.events.ShipmentCancelled;
import org.linlinjava.announce.shipping.domain.events.ShipmentDelivered;
import org.linlinjava.announce.shipping.domain.events.ShipmentDispatched;
import org.linlinjava.announce.shipping.domain.events.ShipmentFailed;
import org.linlinjava.announce.shipping.domain.events.ShipmentInTransit;
import org.linlinjava.announce.shipping.domain.events.ShipmentOpened;
import org.linlinjava.announce.shipping.domain.events.ShipmentRequested;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/** Group {@code shipping-read-model}: {@code announce_shipment}; upsert by id, at-least-once. */
@Component
public class ShipmentReadModelProjection implements ReactiveProjection {

    public static final String GROUP = "shipping-read-model";

    private final AnnounceShipmentMapper shipments;
    private final DatabaseClient db;

    public ShipmentReadModelProjection(AnnounceShipmentMapper shipments, DatabaseClient db) {
        this.shipments = shipments;
        this.db = db;
    }

    @Override
    public String group() {
        return GROUP;
    }

    @Override
    public void configure(ProjectionHandlers handlers) {
        handlers.on(ShipmentOpened.class, (e, env) -> {
            AnnounceShipment row = patch(env, e.at());
            row.setOrderId(idOf(env));
            row.setSellerId(e.sellerId());
            row.setBuyerId(e.buyerId());
            row.setStatus("OPEN");
            row.setAddTime(e.at());
            return shipments.selectByPrimaryKey(row.getId()).hasElement()
                    .flatMap(exists -> exists ? shipments.updateByPrimaryKeySelective(row) : shipments.insertSelective(row)).then();
        });
        handlers.on(ShipmentRequested.class, (e, env) -> update(env, "REQUESTED", e.at(), r -> {
            r.setCarrier(e.carrier()); r.setService(e.service()); r.setParcelSize(e.parcelSize());
            r.setTrackingNumber(e.trackingNumber()); r.setLabelUrl(e.labelUrl()); r.setRequestedAt(e.at());
        }));
        handlers.on(ShipmentDispatched.class, (e, env) -> update(env, "DISPATCHED", e.at(), r -> carrierEvent(r, "DISPATCHED", e.carrierEventId(), e.at(), () -> r.setDispatchedAt(e.at()))));
        handlers.on(ShipmentInTransit.class, (e, env) -> update(env, "IN_TRANSIT", e.at(), r -> carrierEvent(r, "IN_TRANSIT", e.carrierEventId(), e.at(), () -> { })));
        handlers.on(ShipmentDelivered.class, (e, env) -> update(env, "DELIVERED", e.at(), r -> carrierEvent(r, "DELIVERED", e.carrierEventId(), e.at(), () -> r.setDeliveredAt(e.at()))));
        handlers.on(ShipmentFailed.class, (e, env) -> update(env, "FAILED", e.at(), r -> carrierEvent(r, "FAILED", e.carrierEventId(), e.at(), () -> { })));
        handlers.on(ShipmentCancelled.class, (e, env) -> update(env, "CANCELLED", e.at(), r -> { }));
    }

    @Override
    public Mono<Void> reset() {
        return db.sql("delete from announce_shipment").then();
    }

    private interface Patch { void apply(AnnounceShipment row); }

    private static void carrierEvent(AnnounceShipment r, String kind, String eventId, LocalDateTime at, Runnable extra) {
        r.setLastCarrierEvent(kind);
        r.setLastCarrierEventId(eventId);
        r.setLastEventAt(at);
        extra.run();
    }

    private Mono<Void> update(EventEnvelope env, String status, LocalDateTime at, Patch p) {
        AnnounceShipment row = patch(env, at);
        row.setStatus(status);
        p.apply(row);
        return shipments.updateByPrimaryKeySelective(row).then();
    }

    private static AnnounceShipment patch(EventEnvelope env, LocalDateTime at) {
        AnnounceShipment row = new AnnounceShipment();
        row.setId(idOf(env));
        row.setLastEventId(env.eventId());
        row.setUpdateTime(at);
        return row;
    }

    private static String idOf(EventEnvelope env) {
        return Subjects.idOf(env.subject());
    }
}
