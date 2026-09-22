package org.linlinjava.announce.goods.domain.announce.events;

import org.linlinjava.announce.goods.domain.announce.AnnounceContent;
import org.linlinjava.announce.goods.domain.announce.AnnounceKind;

import java.time.LocalDateTime;

/** The content or the kind-specific details changed (the kind itself never does). Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record AnnounceEdited(AnnounceContent content, AnnounceKind details, LocalDateTime at) {
}
