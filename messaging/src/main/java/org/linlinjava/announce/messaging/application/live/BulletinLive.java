package org.linlinjava.announce.messaging.application.live;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;

import java.time.LocalDateTime;

/** A staff bulletin to push to the connected staff sockets of {@code clientType} (null: every client type). */
@PublicEvent(type = "messaging.live.bulletin", version = 1)
public record BulletinLive(Integer id,
                           Integer adminId,
                           String audience,
                           String clientType,
                           String content,
                           LocalDateTime expiresAt,
                           LocalDateTime at) {
}
