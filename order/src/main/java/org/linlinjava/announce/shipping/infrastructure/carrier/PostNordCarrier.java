package org.linlinjava.announce.shipping.infrastructure.carrier;

import org.linlinjava.announce.shipping.domain.Carrier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Stub of the PostNord (DK/SE) adapter, selected with {@code shipping.carrier=postnord}; every call fails until the real integration lands. */
@Component
@ConditionalOnProperty(prefix = "shipping", name = "carrier", havingValue = "postnord")
public class PostNordCarrier implements Carrier {

    @Override
    public String name() {
        return "POSTNORD";
    }

    @Override
    public Mono<Booking> book(BookingRequest request) {
        return Mono.error(new UnsupportedOperationException("PostNord adapter is not implemented yet"));
    }

    @Override
    public Flux<TrackingEvent> track(String trackingNumber) {
        return Flux.error(new UnsupportedOperationException("PostNord adapter is not implemented yet"));
    }
}
