package org.linlinjava.announce.auction.domain.events;

import java.time.LocalDateTime;

/** A bid was accepted and is the current highest; {@code endsAt} is the end after any anti-sniping extension. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record BidPlaced(String bidId,
                        Integer bidderId,
                        long amountCents,
                        Long maxAmountCents,
                        LocalDateTime at,
                        LocalDateTime endsAt) {
}
