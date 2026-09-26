package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;
import java.time.LocalDateTime;

/** An auction opened for a published AUCTION announce (subject /auction/{announceId}). */
@PublicEvent(type = "auction.opened", version = 1)
public record AuctionOpenedV1(String announceId,
                              Integer sellerId,
                              long initialPriceCents,
                              Long reservePriceCents,
                              Long snagItPriceCents,
                              long incrementCents,
                              String currency,
                              LocalDateTime startsAt,
                              LocalDateTime endsAt) {
}
