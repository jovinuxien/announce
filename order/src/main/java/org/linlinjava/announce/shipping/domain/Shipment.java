package org.linlinjava.announce.shipping.domain;

import org.linlinjava.announce.shipping.domain.events.ShipmentCancelled;
import org.linlinjava.announce.shipping.domain.events.ShipmentDelivered;
import org.linlinjava.announce.shipping.domain.events.ShipmentDispatched;
import org.linlinjava.announce.shipping.domain.events.ShipmentFailed;
import org.linlinjava.announce.shipping.domain.events.ShipmentInTransit;
import org.linlinjava.announce.shipping.domain.events.ShipmentOpened;
import org.linlinjava.announce.shipping.domain.events.ShipmentRequested;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/** State of the Shipment aggregate under {@code /shipment/{orderId}}; remembers the carrier event ids it applied. */
public record Shipment(String orderId, Integer sellerId, Integer buyerId, String carrier, String service, String parcelSize, String trackingNumber,
                       String labelUrl, ShipmentStatus status, String lastCarrierEvent, String failureReason, Set<String> carrierEventIds,
                       LocalDateTime requestedAt, LocalDateTime dispatchedAt, LocalDateTime deliveredAt, LocalDateTime updatedAt) {

    public Shipment {
        carrierEventIds = carrierEventIds == null ? Set.of() : Set.copyOf(carrierEventIds);
    }

    public boolean hasSeen(String carrierEventId) {
        return carrierEventId != null && carrierEventIds.contains(carrierEventId);
    }

    public static Shipment apply(String orderId, ShipmentOpened e) {
        return new Shipment(orderId, e.sellerId(), e.buyerId(), null, null, null, null, null, ShipmentStatus.OPEN, null, null, Set.of(), null, null, null, e.at());
    }

    public Shipment apply(ShipmentRequested e) {
        return new Shipment(orderId, sellerId, buyerId, e.carrier(), e.service(), e.parcelSize(), e.trackingNumber(), e.labelUrl(), ShipmentStatus.REQUESTED,
                null, null, carrierEventIds, e.at(), dispatchedAt, deliveredAt, e.at());
    }

    public Shipment apply(ShipmentDispatched e) { return carrier(ShipmentStatus.DISPATCHED, e.carrierEventId(), "DISPATCHED", null, e.at(), e.at(), deliveredAt); }
    public Shipment apply(ShipmentInTransit e) { return carrier(ShipmentStatus.IN_TRANSIT, e.carrierEventId(), "IN_TRANSIT", null, e.at(), dispatchedAt, deliveredAt); }
    public Shipment apply(ShipmentDelivered e) { return carrier(ShipmentStatus.DELIVERED, e.carrierEventId(), "DELIVERED", null, e.at(), dispatchedAt, e.at()); }
    public Shipment apply(ShipmentFailed e) { return carrier(ShipmentStatus.FAILED, e.carrierEventId(), "FAILED", e.reason(), e.at(), dispatchedAt, deliveredAt); }
    public Shipment apply(ShipmentCancelled e) { return carrier(ShipmentStatus.CANCELLED, null, lastCarrierEvent, e.reason(), e.at(), dispatchedAt, deliveredAt); }

    private Shipment carrier(ShipmentStatus newStatus, String eventId, String last, String reason, LocalDateTime at, LocalDateTime dispatched, LocalDateTime delivered) {
        Set<String> ids = new LinkedHashSet<>(carrierEventIds);
        if (eventId != null) ids.add(eventId);
        return new Shipment(orderId, sellerId, buyerId, carrier, service, parcelSize, trackingNumber, labelUrl, newStatus, last, reason, ids,
                requestedAt, dispatched, delivered, at);
    }
}
