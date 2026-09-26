package org.linlinjava.announce.order.domain.events;

import java.time.LocalDateTime;

/** Buyer and seller agreed on a price through one of the three doors; the fee and the payout are fixed here. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record OrderAgreed(String announceId,
                          Integer buyerId,
                          Integer sellerId,
                          String door,
                          String auctionId,
                          String conversationId,
                          long priceCents,
                          long feeCents,
                          long payoutCents,
                          String currency,
                          String title,
                          String mainPicture,
                          LocalDateTime at) {
}
