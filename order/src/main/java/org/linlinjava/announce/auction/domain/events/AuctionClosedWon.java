package org.linlinjava.announce.auction.domain.events;

import java.time.LocalDateTime;

/** The end passed with a highest bid that meets the reserve. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record AuctionClosedWon(Integer winnerId,
                               String winningBidId,
                               long priceCents,
                               LocalDateTime at) {
}
