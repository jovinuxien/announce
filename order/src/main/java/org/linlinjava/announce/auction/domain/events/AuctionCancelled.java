package org.linlinjava.announce.auction.domain.events;

import java.time.LocalDateTime;

/** Cancelled by the seller (no bid yet), by staff, or by the system when the announce went away. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record AuctionCancelled(String reason,
                               Integer actorId,
                               String actorType,
                               LocalDateTime at) {
}
