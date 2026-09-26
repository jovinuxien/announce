package org.linlinjava.announce.goods.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;

import java.time.LocalDateTime;

/**
 * An announce became visible (first publication or a republication after withdraw, expiry or a cancelled deal).
 * Carries the snapshot other contexts need to act without calling back: the auction context opens an auction when
 * {@code saleType} is {@code AUCTION}; the order context stores title, picture and price for its deals.
 *
 * @param kind      GOODS, JOB or EVENT
 * @param priceCents the goods price in minor units (0 for a giveaway, an auction's start price, or a non-goods kind)
 * @param saleType  FIXED_PRICE, AUCTION or GIVEAWAY for goods; null for jobs and events
 */
@PublicEvent(type = "announce.published", version = 1)
public record AnnouncePublishedV1(String announceId,
                                  String kind,
                                  Integer sellerId,
                                  String title,
                                  String mainPicture,
                                  long priceCents,
                                  String currency,
                                  String saleType,
                                  Integer categoryId,
                                  LocalDateTime publishedAt,
                                  LocalDateTime expiresAt) {
}
