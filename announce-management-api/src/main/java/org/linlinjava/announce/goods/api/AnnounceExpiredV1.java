package org.linlinjava.announce.goods.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;

import java.time.LocalDateTime;

/** The announce is no longer visible: expired. */
@PublicEvent(type = "announce.expired", version = 1)
public record AnnounceExpiredV1(String announceId, LocalDateTime at) {
}
