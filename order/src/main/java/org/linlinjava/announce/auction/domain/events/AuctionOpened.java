package org.linlinjava.announce.auction.domain.events;

import java.time.LocalDateTime;

/** An auction opened for a published AUCTION announce; prices in minor units. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record AuctionOpened(Integer sellerId,
                            String title,
                            String mainPicture,
                            String currency,
                            long initialPriceCents,
                            Long reservePriceCents,
                            Long snagItPriceCents,
                            long incrementCents,
                            LocalDateTime startsAt,
                            LocalDateTime endsAt,
                            int autoExtendSec,
                            LocalDateTime at) {
}
