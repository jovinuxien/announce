package org.linlinjava.announce.messaging.domain.conversation;

/** Lifecycle of a conversation: (WAITING ->) OPEN -> CLOSED | BLOCKED. */
public enum ConversationStatus {
    /** SUPPORT only: opened by a member, no staff has joined yet (the CSR queue). */
    WAITING,
    OPEN,
    /** Closed by a participant or staff; no more messages. */
    CLOSED,
    /** Blocked by staff (abuse); no more messages. */
    BLOCKED;

    public boolean writable() {
        return this == WAITING || this == OPEN;
    }
}
