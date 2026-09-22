package org.linlinjava.announce.goods.domain.announce.events;


import java.time.LocalDateTime;

/** The goods were sold (goods only). Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record AnnounceSold(LocalDateTime at) {
}
