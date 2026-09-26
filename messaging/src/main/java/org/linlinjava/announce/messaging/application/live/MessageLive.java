package org.linlinjava.announce.messaging.application.live;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;

import java.time.LocalDateTime;

/**
 * A message to push to the sockets of one conversation. Internal to the messaging service (topic
 * {@code messaging.live}, keyed by conversation); clients de-duplicate on {@code messageId}.
 */
@PublicEvent(type = "messaging.live.message", version = 1)
public record MessageLive(String conversationId,
                          String messageId,
                          int seq,
                          String senderType,
                          Integer senderId,
                          String contentType,
                          String content,
                          String attachmentUrl,
                          Long offerAmountCents,
                          String currency,
                          String clientMsgId,
                          LocalDateTime sentAt) {
}
