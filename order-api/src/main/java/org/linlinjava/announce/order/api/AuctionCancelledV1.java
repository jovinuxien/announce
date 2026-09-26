package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;
import java.time.LocalDateTime;

/** The auction was cancelled by the seller before the first bid, by staff, or because the announce went away. */
@PublicEvent(type = "auction.cancelled", version = 1)
public record AuctionCancelledV1(String announceId,
                                 Integer sellerId,
                                 String reason,
                                 LocalDateTime at) {
}
