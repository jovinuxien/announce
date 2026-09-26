package org.linlinjava.announce.goods.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;

/**
 * The member changed the content of an announce. Published for drafts as well as visible announces: a consumer
 * that keeps a snapshot updates the row it already has and ignores ids it never saw published.
 */
@PublicEvent(type = "announce.edited", version = 1)
public record AnnounceEditedV1(String announceId,
                               String kind,
                               String title,
                               String mainPicture,
                               long priceCents,
                               String currency,
                               String saleType,
                               Integer categoryId) {
}
