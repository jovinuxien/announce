package org.linlinjava.announce.auction.domain.events;

import java.time.LocalDateTime;

/** A buyer took the snag-it price while no bid existed; the auction is closed. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record AuctionSnagged(Integer buyerId,
                             long priceCents,
                             LocalDateTime at) {
}
