package org.linlinjava.announce.messaging.domain.conversation;

import java.time.LocalDateTime;

/**
 * Result of {@code PostMessage}: the message id and seq the conversation assigned, or the original ones when the
 * client message id was seen before ({@code duplicate}).
 */
public record PostResult(String conversationId, String messageId, int seq, String clientMsgId, boolean duplicate, LocalDateTime at) {
}
