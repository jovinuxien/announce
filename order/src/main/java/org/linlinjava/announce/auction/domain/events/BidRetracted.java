package org.linlinjava.announce.auction.domain.events;

import java.time.LocalDateTime;

/** The bidder withdrew a bid; the next highest bid becomes the current one. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record BidRetracted(String bidId,
                           Integer bidderId,
                           String newActiveBidId,
                           long newCurrentPriceCents,
                           LocalDateTime at) {
}
