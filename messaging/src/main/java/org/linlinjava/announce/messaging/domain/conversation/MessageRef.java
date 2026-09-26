package org.linlinjava.announce.messaging.domain.conversation;

/** Where a message landed in the conversation; what a duplicate post is answered with. */
public record MessageRef(String messageId, int seq) {
}
