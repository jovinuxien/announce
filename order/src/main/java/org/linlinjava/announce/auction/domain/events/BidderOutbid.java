package org.linlinjava.announce.auction.domain.events;

import java.time.LocalDateTime;

/** The previous highest bid was beaten. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record BidderOutbid(String bidId,
                           Integer bidderId,
                           String byBidId,
                           LocalDateTime at) {
}
