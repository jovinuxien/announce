package org.linlinjava.announce.messaging.domain.conversation;

import java.util.Objects;

/** Someone taking part in a conversation: a member or a staff member, with the role they play. */
public record Participant(ParticipantType type, Integer id, ParticipantRole role) {

    public Participant {
        Objects.requireNonNull(type, "participant type");
        Objects.requireNonNull(id, "participant id");
        Objects.requireNonNull(role, "participant role");
    }

    public static Participant buyer(Integer id) {
        return new Participant(ParticipantType.USER, id, ParticipantRole.BUYER);
    }

    public static Participant seller(Integer id) {
        return new Participant(ParticipantType.USER, id, ParticipantRole.SELLER);
    }

    public static Participant member(Integer id) {
        return new Participant(ParticipantType.USER, id, ParticipantRole.MEMBER);
    }

    public static Participant csr(Integer id) {
        return new Participant(ParticipantType.ADMIN, id, ParticipantRole.CSR);
    }

    public boolean matches(ParticipantType candidateType, Integer candidateId) {
        return type == candidateType && id.equals(candidateId);
    }

    public Sender asSender() {
        return new Sender(type == ParticipantType.ADMIN ? SenderType.ADMIN : SenderType.USER, id);
    }
}
