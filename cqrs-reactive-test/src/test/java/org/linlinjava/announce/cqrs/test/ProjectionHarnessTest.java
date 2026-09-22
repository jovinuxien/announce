package org.linlinjava.announce.cqrs.test;

import org.junit.jupiter.api.Test;
import org.linlinjava.announce.cqrs.readside.ProjectionHandlers;
import org.linlinjava.announce.cqrs.readside.ReactiveProjection;
import org.linlinjava.announce.cqrs.sample.ListingClosed;
import org.linlinjava.announce.cqrs.sample.ListingCreated;
import org.linlinjava.announce.cqrs.sample.ListingPriceChanged;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectionHarnessTest {

    static class InMemoryPrices implements ReactiveProjection {
        final Map<String, Long> prices = new LinkedHashMap<>();
        int prepared;

        @Override
        public String group() {
            return "prices";
        }

        @Override
        public void configure(ProjectionHandlers handlers) {
            handlers.on(ListingCreated.class, (event, envelope) -> Mono.fromRunnable(() -> prices.put(envelope.subject(), event.priceCents())));
            handlers.on(ListingPriceChanged.class, (event, envelope) -> Mono.fromRunnable(() -> prices.put(envelope.subject(), event.priceCents())));
        }

        @Override
        public Mono<Void> prepare() {
            return Mono.fromRunnable(() -> prepared++);
        }

        @Override
        public Mono<Void> reset() {
            return Mono.fromRunnable(prices::clear);
        }
    }

    @Test
    void feedsEventsThroughTheAdapter() {
        InMemoryPrices projection = new InMemoryPrices();
        ProjectionHarness harness = ProjectionHarness.of(projection).prepare()
                .feed("/listing/1", new ListingCreated("Bike", 12000))
                .feed("/listing/2", new ListingCreated("Sofa", 5000))
                .feed("/listing/1", new ListingPriceChanged(9900))
                .feed("/listing/1", new ListingClosed());

        assertThat(projection.prepared).isEqualTo(1);
        assertThat(projection.prices).containsExactly(Map.entry("/listing/1", 9900L), Map.entry("/listing/2", 5000L));
        assertThat(harness.handled()).hasSize(4);
        assertThat(harness.handled().get(0).eventId()).isEqualTo("1");
        assertThat(harness.handled().get(0).correlationId()).contains("test-correlation");

        harness.reset();
        assertThat(projection.prices).isEmpty();
    }
}
