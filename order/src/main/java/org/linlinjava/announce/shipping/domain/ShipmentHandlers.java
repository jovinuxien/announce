package org.linlinjava.announce.shipping.domain;

import com.opencqrs.framework.command.CommandHandler;
import com.opencqrs.framework.command.CommandHandlerDefinition;
import com.opencqrs.framework.command.StateRebuildingHandler;
import com.opencqrs.framework.command.StateRebuildingHandlerDefinition;
import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.cqrs.command.Subjects;
import org.linlinjava.announce.shipping.domain.ShipmentCommands.CancelShipment;
import org.linlinjava.announce.shipping.domain.ShipmentCommands.OpenShipment;
import org.linlinjava.announce.shipping.domain.ShipmentCommands.RecordCarrierEvent;
import org.linlinjava.announce.shipping.domain.ShipmentCommands.RequestShipment;
import org.linlinjava.announce.shipping.domain.events.ShipmentCancelled;
import org.linlinjava.announce.shipping.domain.events.ShipmentDelivered;
import org.linlinjava.announce.shipping.domain.events.ShipmentDispatched;
import org.linlinjava.announce.shipping.domain.events.ShipmentFailed;
import org.linlinjava.announce.shipping.domain.events.ShipmentInTransit;
import org.linlinjava.announce.shipping.domain.events.ShipmentOpened;
import org.linlinjava.announce.shipping.domain.events.ShipmentRequested;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Handlers of the Shipment aggregate. Carrier events already applied are no-ops; out-of-order ones are tolerated (a delivery may arrive before a dispatch scan). */
@Configuration(proxyBeanMethods = false)
public class ShipmentHandlers {

    public static final StateRebuildingHandlerDefinition<Shipment, ShipmentOpened> ON_OPENED = new StateRebuildingHandlerDefinition<>(
            Shipment.class, ShipmentOpened.class,
            (StateRebuildingHandler.FromObjectAndMetaDataAndSubject<Shipment, ShipmentOpened>) (s, e, m, subject) -> Shipment.apply(Subjects.idOf(subject), e));
    public static final StateRebuildingHandlerDefinition<Shipment, ShipmentRequested> ON_REQUESTED = def(ShipmentRequested.class, Shipment::apply);
    public static final StateRebuildingHandlerDefinition<Shipment, ShipmentDispatched> ON_DISPATCHED = def(ShipmentDispatched.class, Shipment::apply);
    public static final StateRebuildingHandlerDefinition<Shipment, ShipmentInTransit> ON_IN_TRANSIT = def(ShipmentInTransit.class, Shipment::apply);
    public static final StateRebuildingHandlerDefinition<Shipment, ShipmentDelivered> ON_DELIVERED = def(ShipmentDelivered.class, Shipment::apply);
    public static final StateRebuildingHandlerDefinition<Shipment, ShipmentFailed> ON_FAILED = def(ShipmentFailed.class, Shipment::apply);
    public static final StateRebuildingHandlerDefinition<Shipment, ShipmentCancelled> ON_CANCELLED = def(ShipmentCancelled.class, Shipment::apply);

    private static <E> StateRebuildingHandlerDefinition<Shipment, E> def(Class<E> type, StateRebuildingHandler.FromObject<Shipment, E> fn) {
        return new StateRebuildingHandlerDefinition<>(Shipment.class, type, fn);
    }

    public static CommandHandlerDefinition<Shipment, OpenShipment, Shipment> open(Clock clock) {
        return new CommandHandlerDefinition<>(Shipment.class, OpenShipment.class,
                (CommandHandler.ForCommand<Shipment, OpenShipment, Shipment>) (c, publisher) -> {
                    if (c.sellerId() == null || c.buyerId() == null) throw new IllegalArgumentException("seller and buyer are required");
                    ShipmentOpened e = new ShipmentOpened(c.sellerId(), c.buyerId(), now(clock));
                    publisher.publish(e);
                    return Shipment.apply(c.orderId(), e);
                });
    }

    public static CommandHandlerDefinition<Shipment, RequestShipment, Shipment> request(Clock clock) {
        return new CommandHandlerDefinition<>(Shipment.class, RequestShipment.class,
                (CommandHandler.ForInstanceAndCommand<Shipment, RequestShipment, Shipment>) (state, c, publisher) -> {
                    if (!state.sellerId().equals(c.sellerId())) throw new CommandRejectedException(HttpStatus.FORBIDDEN, "not-seller", "Only the seller books the shipment");
                    if (state.status() != ShipmentStatus.OPEN) throw reject("already-requested", "The shipment is " + state.status());
                    if (c.trackingNumber() == null || c.trackingNumber().isBlank()) throw new IllegalArgumentException("tracking number is required");
                    ShipmentRequested e = new ShipmentRequested(state.sellerId(), state.buyerId(), c.carrier(), c.service(), c.parcelSize(),
                            c.trackingNumber(), c.labelUrl(), now(clock));
                    publisher.publish(e);
                    return state.apply(e);
                });
    }

    public static CommandHandlerDefinition<Shipment, RecordCarrierEvent, Shipment> recordCarrierEvent(Clock clock) {
        return new CommandHandlerDefinition<>(Shipment.class, RecordCarrierEvent.class,
                (CommandHandler.ForInstanceAndCommand<Shipment, RecordCarrierEvent, Shipment>) (state, c, publisher) -> {
                    if (state.hasSeen(c.carrierEventId())) return state;
                    if (!state.status().isTracked()) {
                        if (state.status() == ShipmentStatus.DELIVERED || state.status() == ShipmentStatus.FAILED) return state;   // final: nothing more to learn
                        throw reject("not-booked", "The shipment is " + state.status() + "; no carrier event expected");
                    }
                    LocalDateTime at = c.at() == null ? now(clock) : c.at();
                    Object e = switch (c.kind()) {
                        case DISPATCHED -> state.status() == ShipmentStatus.REQUESTED ? new ShipmentDispatched(c.carrierEventId(), state.carrier(), state.trackingNumber(), at) : null;
                        case IN_TRANSIT -> new ShipmentInTransit(c.carrierEventId(), state.carrier(), state.trackingNumber(), at);
                        case DELIVERED -> new ShipmentDelivered(c.carrierEventId(), state.carrier(), state.trackingNumber(), at);
                        case FAILED -> new ShipmentFailed(c.carrierEventId(), state.carrier(), state.trackingNumber(), c.reason(), at);
                    };
                    if (e == null) return state;   // a late dispatch scan after later events
                    publisher.publish(e);
                    return switch (e) {
                        case ShipmentDispatched d -> state.apply(d);
                        case ShipmentInTransit t -> state.apply(t);
                        case ShipmentDelivered d -> state.apply(d);
                        case ShipmentFailed f -> state.apply(f);
                        default -> state;
                    };
                });
    }

    public static CommandHandlerDefinition<Shipment, CancelShipment, Shipment> cancel(Clock clock) {
        return new CommandHandlerDefinition<>(Shipment.class, CancelShipment.class,
                (CommandHandler.ForInstanceAndCommand<Shipment, CancelShipment, Shipment>) (state, c, publisher) -> {
                    if (state.status() != ShipmentStatus.OPEN && state.status() != ShipmentStatus.REQUESTED) return state;
                    ShipmentCancelled e = new ShipmentCancelled(c.reason(), now(clock));
                    publisher.publish(e);
                    return state.apply(e);
                });
    }

    public static List<Object> definitions(Clock clock) {
        return List.of(ON_OPENED, ON_REQUESTED, ON_DISPATCHED, ON_IN_TRANSIT, ON_DELIVERED, ON_FAILED, ON_CANCELLED,
                open(clock), request(clock), recordCarrierEvent(clock), cancel(clock));
    }

    private static CommandRejectedException reject(String code, String detail) {
        return new CommandRejectedException(HttpStatus.CONFLICT, code, detail);
    }

    private static LocalDateTime now(Clock clock) {
        return LocalDateTime.now(clock);
    }

    @Bean StateRebuildingHandlerDefinition<Shipment, ShipmentOpened> shipmentOnOpened() { return ON_OPENED; }
    @Bean StateRebuildingHandlerDefinition<Shipment, ShipmentRequested> shipmentOnRequested() { return ON_REQUESTED; }
    @Bean StateRebuildingHandlerDefinition<Shipment, ShipmentDispatched> shipmentOnDispatched() { return ON_DISPATCHED; }
    @Bean StateRebuildingHandlerDefinition<Shipment, ShipmentInTransit> shipmentOnInTransit() { return ON_IN_TRANSIT; }
    @Bean StateRebuildingHandlerDefinition<Shipment, ShipmentDelivered> shipmentOnDelivered() { return ON_DELIVERED; }
    @Bean StateRebuildingHandlerDefinition<Shipment, ShipmentFailed> shipmentOnFailed() { return ON_FAILED; }
    @Bean StateRebuildingHandlerDefinition<Shipment, ShipmentCancelled> shipmentOnCancelled() { return ON_CANCELLED; }
    @Bean CommandHandlerDefinition<Shipment, OpenShipment, Shipment> shipmentOpen(Clock clock) { return open(clock); }
    @Bean CommandHandlerDefinition<Shipment, RequestShipment, Shipment> shipmentRequest(Clock clock) { return request(clock); }
    @Bean CommandHandlerDefinition<Shipment, RecordCarrierEvent, Shipment> shipmentRecordCarrierEvent(Clock clock) { return recordCarrierEvent(clock); }
    @Bean CommandHandlerDefinition<Shipment, CancelShipment, Shipment> shipmentCancel(Clock clock) { return cancel(clock); }
}
