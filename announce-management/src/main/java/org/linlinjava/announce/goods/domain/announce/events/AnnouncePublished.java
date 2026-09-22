package org.linlinjava.announce.goods.domain.announce.events;


import java.time.LocalDateTime;

/** The announce became visible until {@code expiresAt}. Search indexes it; the auction context opens an auction for biddable goods. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record AnnouncePublished(LocalDateTime at, LocalDateTime expiresAt) {
}
