package org.linlinjava.announce.messaging.domain.conversation;

/** Which table a participant id refers to. */
public enum ParticipantType {
    /** A member, {@code announce_user.id}. */
    USER,
    /** A staff member, {@code announce_admin.id}. */
    ADMIN
}
