package org.linlinjava.announce.goods.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;

import java.time.LocalDateTime;

/** The announce is no longer visible: withdrawn. */
@PublicEvent(type = "announce.withdrawn", version = 1)
public record AnnounceWithdrawnV1(String announceId, LocalDateTime at) {
}
