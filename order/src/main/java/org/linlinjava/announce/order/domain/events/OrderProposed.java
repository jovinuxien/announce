package org.linlinjava.announce.order.domain.events;

import java.time.LocalDateTime;

/** A buyer asked to buy a fixed-price announce; the seller has to accept. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record OrderProposed(String announceId,
                            Integer buyerId,
                            Integer sellerId,
                            long priceCents,
                            String currency,
                            String title,
                            String mainPicture,
                            Integer categoryId,
                            LocalDateTime at) {
}
