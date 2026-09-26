package org.linlinjava.announce.auction.domain.events;

import java.time.LocalDateTime;

/** A bid inside the auto-extend window pushed the end forward. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record AuctionExtended(LocalDateTime previousEndsAt,
                              LocalDateTime endsAt,
                              LocalDateTime at) {
}
