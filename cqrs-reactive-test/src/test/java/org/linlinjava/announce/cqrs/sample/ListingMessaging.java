package org.linlinjava.announce.cqrs.sample;

import org.linlinjava.announce.cqrs.command.Subjects;
import org.linlinjava.announce.cqrs.messaging.PublicEventRelay;
import org.linlinjava.announce.cqrs.messaging.PublicEventRelays;
import org.linlinjava.announce.cqrs.messaging.PublicEventSubscriber;
import org.linlinjava.announce.cqrs.messaging.PublicEventSubscribers;
import org.linlinjava.announce.cqrs.sample.ListingPublicEvents.ListingCreatedV1;
import org.linlinjava.announce.cqrs.sample.ListingPublicEvents.ListingPriceChangedV1;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Relay of internal listing events to the public topic {@code listing.v1} (binding {@code listing-out-0})
 * and a subscriber of that same topic (binding {@code listingEvents-in-0}) standing in for another service,
 * for example the search indexer.
 */
@Configuration(proxyBeanMethods = false)
public class ListingMessaging {

    public static final String RELAY_GROUP = "listing-public";

    /** What the subscriber received: the public event and the headers it came with. */
    public record Received(Object event, PublicEventSubscriber.Headers headers) {
    }

    public static class ReceivedPublicEvents {
        private final List<Received> received = new CopyOnWriteArrayList<>();

        public List<Received> all() {
            return List.copyOf(received);
        }

        void add(Received item) {
            received.add(item);
        }
    }

    @Bean
    PublicEventRelay listingPublicEvents(PublicEventRelays relays) {
        return relays.forGroup(RELAY_GROUP).binding("listing-out-0")
                .map(ListingCreated.class, ListingCreatedV1.class, (event, envelope) ->
                        new ListingCreatedV1(Subjects.idOf(envelope.subject()), event.title(), event.priceCents()))
                .map(ListingPriceChanged.class, ListingPriceChangedV1.class, (event, envelope) ->
                        new ListingPriceChangedV1(Subjects.idOf(envelope.subject()), event.priceCents()))
                .build();
    }

    @Bean
    ReceivedPublicEvents receivedPublicEvents() {
        return new ReceivedPublicEvents();
    }

    @Bean
    Consumer<Message<byte[]>> listingEvents(PublicEventSubscribers subscribers, ReceivedPublicEvents received) {
        return subscribers.forTopic("listing")
                .on(ListingCreatedV1.class, (event, headers) -> Mono.fromRunnable(() -> received.add(new Received(event, headers))))
                .on(ListingPriceChangedV1.class, (event, headers) -> Mono.fromRunnable(() -> received.add(new Received(event, headers))))
                .build();
    }
}
