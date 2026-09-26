package org.linlinjava.announce.messaging.domain.conversation.events;

import org.linlinjava.announce.messaging.domain.conversation.ContentType;
import org.linlinjava.announce.messaging.domain.conversation.Sender;

import java.time.LocalDateTime;

/**
 * A message was posted. {@code seq} is its 1-based position in the conversation; {@code clientMsgId} is the
 * client's idempotency key (null for messages the platform posts without one).
 */
public record MessagePosted(String messageId,
                            int seq,
                            Sender sender,
                            ContentType contentType,
                            String content,
                            String attachmentUrl,
                            Long offerAmountCents,
                            String currency,
                            String clientMsgId,
                            LocalDateTime at) {
}
