package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;
import java.time.LocalDateTime;

/** A buyer took the snag-it price while no bid existed; the order context creates the deal (door AUCTION). */
@PublicEvent(type = "auction.snagged", version = 1)
public record AuctionSnaggedV1(String announceId,
                               Integer sellerId,
                               Integer buyerId,
                               long priceCents,
                               String currency,
                               LocalDateTime at) {
}
