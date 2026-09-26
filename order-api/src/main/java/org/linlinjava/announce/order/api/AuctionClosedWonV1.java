package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;
import java.time.LocalDateTime;

/** The auction ended with a winner; the order context creates the deal (door AUCTION). */
@PublicEvent(type = "auction.closed-won", version = 1)
public record AuctionClosedWonV1(String announceId,
                                 Integer sellerId,
                                 Integer winnerId,
                                 String winningBidId,
                                 long priceCents,
                                 String currency,
                                 LocalDateTime closedAt) {
}
