package org.linlinjava.announce.messaging.domain.conversation;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;

/**
 * The author of a message: a member ({@code USER}), a staff member ({@code ADMIN}) or the platform itself
 * ({@code SYSTEM}, no id; posted by subscribers of other contexts' events).
 */
public record Sender(SenderType type, Integer id) {

    public Sender {
        Objects.requireNonNull(type, "sender type");
        if (type != SenderType.SYSTEM && id == null) {
            throw new IllegalArgumentException("A " + type + " sender needs an id");
        }
    }

    public static Sender system() {
        return new Sender(SenderType.SYSTEM, null);
    }

    public static Sender user(Integer id) {
        return new Sender(SenderType.USER, id);
    }

    public static Sender staff(Integer id) {
        return new Sender(SenderType.ADMIN, id);
    }

    @JsonIgnore
    public boolean isSystem() {
        return type == SenderType.SYSTEM;
    }

    @JsonIgnore
    public boolean isStaff() {
        return type == SenderType.ADMIN;
    }

    /** The participant type this sender would be looked up as; null for SYSTEM. */
    @JsonIgnore
    public ParticipantType participantType() {
        return switch (type) {
            case USER -> ParticipantType.USER;
            case ADMIN -> ParticipantType.ADMIN;
            case SYSTEM -> null;
        };
    }
}
