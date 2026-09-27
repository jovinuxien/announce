package org.linlinjava.announce.shipping.infrastructure.carrier;

import org.linlinjava.announce.shipping.domain.Carrier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory carrier for tests and local development: booking answers a fake label and tracking number; tracking
 * answers whatever was {@link #push pushed} for that tracking number (so the polling job can be exercised), and
 * {@code POST /api/shipping/webhook/fake} simulates a pushed event.
 */
@Component
@ConditionalOnProperty(prefix = "shipping", name = "carrier", havingValue = "fake", matchIfMissing = true)
public class FakeCarrier implements Carrier {

    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, List<TrackingEvent>> events = new ConcurrentHashMap<>();
    private final List<BookingRequest> bookings = new CopyOnWriteArrayList<>();

    @Override
    public String name() {
        return "FAKE";
    }

    @Override
    public Mono<Booking> book(BookingRequest request) {
        return Mono.fromSupplier(() -> {
            bookings.add(request);
            String tracking = "FAKE" + String.format("%08d", sequence.incrementAndGet());
            return new Booking(name(), request.service() == null ? "standard" : request.service(), tracking, "https://fake-carrier.invalid/labels/" + tracking + ".pdf");
        });
    }

    @Override
    public Flux<TrackingEvent> track(String trackingNumber) {
        return Flux.fromIterable(events.getOrDefault(trackingNumber, List.of()));
    }

    /** Simulates a tracking event the next poll will find. */
    public void push(String trackingNumber, TrackingEvent event) {
        events.computeIfAbsent(trackingNumber, k -> new CopyOnWriteArrayList<>()).add(event);
    }

    public List<BookingRequest> bookings() {
        return List.copyOf(bookings);
    }
}
