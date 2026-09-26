package org.linlinjava.announce.messaging.domain.conversation;

/** What a conversation is about. */
public enum ConversationType {
    /** Buyer and seller about one announce; exactly one per (announce, buyer). */
    LISTING,
    /** A member and the support staff; WAITING until a staff member joins. */
    SUPPORT
}
