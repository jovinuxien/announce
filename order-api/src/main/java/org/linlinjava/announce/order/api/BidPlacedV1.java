package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;
import java.time.LocalDateTime;

/** A bid was accepted; {@code endsAt} carries any anti-sniping extension. */
@PublicEvent(type = "auction.bid-placed", version = 1)
public record BidPlacedV1(String announceId,
                          String bidId,
                          Integer bidderId,
                          long amountCents,
                          String currency,
                          LocalDateTime at,
                          LocalDateTime endsAt) {
}
