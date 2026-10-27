package org.linlinjava.announce.auction.domain.events;

import java.time.LocalDateTime;

/**
 * The seller moved the auction window on the announce before the first bid (announce.v1 {@code AnnounceEditedV1}
 * with dates). Top-level class: EventSourcingDB rejects the {@code $} of a nested class name.
 */
public record AuctionRescheduled(LocalDateTime previousStartsAt,
                                 LocalDateTime previousEndsAt,
                                 LocalDateTime startsAt,
                                 LocalDateTime endsAt,
                                 LocalDateTime at) {
}
