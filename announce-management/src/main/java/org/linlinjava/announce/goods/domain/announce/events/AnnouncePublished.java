package org.linlinjava.announce.goods.domain.announce.events;

import org.linlinjava.announce.goods.domain.announce.AnnounceContent;
import org.linlinjava.announce.goods.domain.announce.AnnounceKind;
import org.linlinjava.announce.goods.domain.announce.SellerId;

import java.time.LocalDateTime;

/**
 * The announce became visible until {@code expiresAt}: first publication, a republication after withdraw or expiry,
 * or a relisting after a cancelled deal. Carries the owner, content and details as they were at that moment so the
 * public event relay can build {@code AnnouncePublishedV1} (title, price, sale type, category) from the event alone:
 * search indexes it; the auction context opens an auction for biddable goods; the order context keeps a snapshot.
 * Top-level class: EventSourcingDB rejects the {@code $} of a nested class name.
 */
public record AnnouncePublished(LocalDateTime at,
                                LocalDateTime expiresAt,
                                SellerId sellerId,
                                AnnounceContent content,
                                AnnounceKind details) {
}
