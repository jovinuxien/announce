package org.linlinjava.announce.auction.domain.events;

import java.time.LocalDateTime;

/** Staff removed a bid; the next highest bid becomes the current one. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record BidCancelled(String bidId,
                           Integer adminId,
                           String reason,
                           String newActiveBidId,
                           long newCurrentPriceCents,
                           LocalDateTime at) {
}
