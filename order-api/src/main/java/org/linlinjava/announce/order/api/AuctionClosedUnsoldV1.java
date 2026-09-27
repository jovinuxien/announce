package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;
import java.time.LocalDateTime;

/** The auction ended without a winner (no bid, or the reserve was not met). */
@PublicEvent(type = "auction.closed-unsold", version = 1)
public record AuctionClosedUnsoldV1(String announceId,
                                    Integer sellerId,
                                    String reason,
                                    LocalDateTime closedAt) {
}
