package org.linlinjava.announce.goods.domain.announce.events;

import org.linlinjava.announce.goods.domain.announce.AnnounceContent;
import org.linlinjava.announce.goods.domain.announce.AnnounceKind;
import org.linlinjava.announce.goods.domain.announce.SellerId;

import java.time.LocalDateTime;

/** A member created a draft announce of one kind. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record AnnouncePosted(SellerId sellerId, AnnounceContent content, AnnounceKind details, LocalDateTime at) {
}
