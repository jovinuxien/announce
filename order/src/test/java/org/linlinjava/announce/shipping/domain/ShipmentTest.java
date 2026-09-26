package org.linlinjava.announce.shipping.domain;

import org.junit.jupiter.api.Test;
import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.cqrs.test.CommandFixture;
import org.linlinjava.announce.shipping.domain.ShipmentCommands.CancelShipment;
import org.linlinjava.announce.shipping.domain.ShipmentCommands.OpenShipment;
import org.linlinjava.announce.shipping.domain.ShipmentCommands.RecordCarrierEvent;
import org.linlinjava.announce.shipping.domain.ShipmentCommands.RequestShipment;
import org.linlinjava.announce.shipping.domain.events.ShipmentCancelled;
import org.linlinjava.announce.shipping.domain.events.ShipmentDelivered;
import org.linlinjava.announce.shipping.domain.events.ShipmentDispatched;
import org.linlinjava.announce.shipping.domain.events.ShipmentOpened;
import org.linlinjava.announce.shipping.domain.events.ShipmentRequested;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Shipment aggregate: only the seller books, carrier events are idempotent, cancel stops at dispatch. */
class ShipmentTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"), ZoneOffset.UTC);
    static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    static final String ORDER = "o-1";

    static CommandFixture<Shipment> fixture() {
        return CommandFixture.forAggregate(Shipment.class).handlers(ShipmentHandlers.definitions(CLOCK)).subject(ShipmentCommands.subjectOf(ORDER));
    }

    static ShipmentOpened opened() {
        return new ShipmentOpened(7, 9, NOW.minusHours(2));
    }

    static ShipmentRequested requested() {
        return new ShipmentRequested(7, 9, "FAKE", "standard", "M", "T1", "http://label", NOW.minusHours(1));
    }

    @Test
    void onlyTheSellerBooksAndOnlyOnce() {
        fixture().when(new OpenShipment(ORDER, 7, 9)).thenExpectEvents(new ShipmentOpened(7, 9, NOW));
        fixture().given(opened()).when(new RequestShipment(ORDER, 7, "FAKE", "standard", "M", "T1", "http://label"))
                .thenExpectEvents(new ShipmentRequested(7, 9, "FAKE", "standard", "M", "T1", "http://label", NOW));
        assertEquals("not-seller", rejected(fixture().given(opened()).when(new RequestShipment(ORDER, 9, "FAKE", "standard", "M", "T1", "http://label"))).getCode());
        assertEquals("already-requested", rejected(fixture().given(opened(), requested()).when(new RequestShipment(ORDER, 7, "FAKE", "standard", "M", "T2", "http://label"))).getCode());
    }

    @Test
    void carrierEventsAreIdempotentAndTolerateOrder() {
        fixture().given(opened(), requested()).when(new RecordCarrierEvent(ORDER, "c-1", CarrierEventKind.DISPATCHED, null, NOW))
                .thenExpectEvents(new ShipmentDispatched("c-1", "FAKE", "T1", NOW));
        fixture().given(opened(), requested(), new ShipmentDispatched("c-1", "FAKE", "T1", NOW)).when(new RecordCarrierEvent(ORDER, "c-1", CarrierEventKind.DISPATCHED, null, NOW))
                .thenExpectNoEvents();
        // delivered straight from requested (the dispatch scan never came)
        fixture().given(opened(), requested()).when(new RecordCarrierEvent(ORDER, "c-2", CarrierEventKind.DELIVERED, null, NOW))
                .thenExpectEvents(new ShipmentDelivered("c-2", "FAKE", "T1", NOW));
        // nothing after delivery
        fixture().given(opened(), requested(), new ShipmentDelivered("c-2", "FAKE", "T1", NOW)).when(new RecordCarrierEvent(ORDER, "c-3", CarrierEventKind.IN_TRANSIT, null, NOW))
                .thenExpectNoEvents();
        assertEquals("not-booked", rejected(fixture().given(opened()).when(new RecordCarrierEvent(ORDER, "c-1", CarrierEventKind.DISPATCHED, null, NOW))).getCode());
    }

    @Test
    void cancelStopsAtDispatch() {
        fixture().given(opened(), requested()).when(new CancelShipment(ORDER, "order refunded")).thenExpectEvents(new ShipmentCancelled("order refunded", NOW));
        fixture().given(opened(), requested(), new ShipmentDispatched("c-1", "FAKE", "T1", NOW)).when(new CancelShipment(ORDER, "x")).thenExpectNoEvents();
    }

    private static CommandRejectedException rejected(CommandFixture<Shipment>.Then then) {
        return then.thenExpectException(CommandRejectedException.class);
    }
}
