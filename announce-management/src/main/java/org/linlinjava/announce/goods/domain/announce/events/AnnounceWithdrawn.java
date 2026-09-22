package org.linlinjava.announce.goods.domain.announce.events;


import java.time.LocalDateTime;

/** The owner took the announce down; it can be published again. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record AnnounceWithdrawn(LocalDateTime at) {
}
