package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;

import java.time.LocalDateTime;

/** The auction window moved before the first bid, following the seller's edit of the announce schedule. */
@PublicEvent(type = "auction.rescheduled", version = 1)
public record AuctionRescheduledV1(String announceId,
                                   Integer sellerId,
                                   LocalDateTime previousStartsAt,
                                   LocalDateTime previousEndsAt,
                                   LocalDateTime startsAt,
                                   LocalDateTime endsAt,
                                   LocalDateTime at) {
}
