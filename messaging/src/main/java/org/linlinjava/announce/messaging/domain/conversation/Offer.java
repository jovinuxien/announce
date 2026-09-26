package org.linlinjava.announce.messaging.domain.conversation;

/** A price proposal made in an OFFER message. */
public record Offer(String messageId, long amountCents, String currency) {
}
