package org.linlinjava.announce.cqrs.sample;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;

/**
 * The published language of the sample service. In a real service these records live in the
 * {@code <name>-api} module.
 */
public final class ListingPublicEvents {

    private ListingPublicEvents() {
    }

    @PublicEvent(type = "listing.created", version = 1)
    public record ListingCreatedV1(String listingId, String title, long priceCents) {
    }

    @PublicEvent(type = "listing.price-changed", version = 1)
    public record ListingPriceChangedV1(String listingId, long priceCents) {
    }
}
