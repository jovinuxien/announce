package org.linlinjava.announce.auction.domain.events;

import java.time.LocalDateTime;

/** The end passed without a bid, or the reserve was not met. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record AuctionClosedUnsold(String reason,
                                  LocalDateTime at) {
}
