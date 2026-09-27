package org.linlinjava.announce.goods.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;

import java.time.LocalDateTime;

/** The announce is no longer visible: sold. */
@PublicEvent(type = "announce.sold", version = 1)
public record AnnounceSoldV1(String announceId, LocalDateTime at) {
}
