package org.linlinjava.announce.shipping.domain;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * The carrier port ({@code doc/order-context.md} §4): book a shipment (label or drop-off code) and read its tracking
 * events. Adapters: {@code FakeCarrier} (tests, local dev; a REST hook simulates events) and {@code PostNordCarrier}
 * (stub). Carriers that push webhooks are consumed like payment webhooks; the others are polled by
 * {@code ShipmentTrackingJob}.
 */
public interface Carrier {

    String name();

    record BookingRequest(String orderId, String service, String parcelSize, String fromAddress, String toAddress) {}
    record Booking(String carrier, String service, String trackingNumber, String labelUrl) {}
    record TrackingEvent(String carrierEventId, CarrierEventKind kind, LocalDateTime at, String reason) {}

    Mono<Booking> book(BookingRequest request);

    Flux<TrackingEvent> track(String trackingNumber);
}
