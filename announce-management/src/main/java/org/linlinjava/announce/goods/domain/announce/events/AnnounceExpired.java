package org.linlinjava.announce.goods.domain.announce.events;


import java.time.LocalDateTime;

/** The validity period ended; the announce can be published again. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record AnnounceExpired(LocalDateTime at) {
}
