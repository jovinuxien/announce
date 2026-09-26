package org.linlinjava.announce.messaging.interfaces;

import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.messaging.application.ConversationQueries.Viewer;
import org.linlinjava.announce.messaging.domain.conversation.ParticipantType;
import org.linlinjava.announce.messaging.domain.conversation.Sender;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * Who is calling, until the gateway propagates the authenticated principal: a member is identified by the
 * {@code X-User-Id} header, a staff member by {@code X-Staff-Id}. Writes may carry {@code X-Command-Id} for
 * idempotent dispatch. This service must not be exposed directly to the internet while headers are trusted.
 */
public final class Principals {

    public static final String USER_HEADER = "X-User-Id";
    public static final String STAFF_HEADER = "X-Staff-Id";
    public static final String COMMAND_ID_HEADER = "X-Command-Id";
    public static final String NO_PRINCIPAL = "no-principal";
    public static final String NOT_STAFF = "not-staff";

    private Principals() {
    }

    /** The member or staff member behind the headers; 401 when neither header is present. */
    public static Viewer viewer(HttpHeaders headers) {
        Integer user = id(headers.getFirst(USER_HEADER), USER_HEADER);
        Integer staff = id(headers.getFirst(STAFF_HEADER), STAFF_HEADER);
        if (staff != null) {
            return new Viewer(ParticipantType.ADMIN, staff);
        }
        if (user != null) {
            return new Viewer(ParticipantType.USER, user);
        }
        throw new CommandRejectedException(HttpStatus.UNAUTHORIZED, NO_PRINCIPAL, "Send " + USER_HEADER + " or " + STAFF_HEADER);
    }

    public static Viewer user(Integer userId) {
        if (userId == null) {
            throw new CommandRejectedException(HttpStatus.UNAUTHORIZED, NO_PRINCIPAL, "Send " + USER_HEADER);
        }
        return new Viewer(ParticipantType.USER, userId);
    }

    public static Viewer staff(Integer staffId) {
        if (staffId == null) {
            throw new CommandRejectedException(HttpStatus.UNAUTHORIZED, NOT_STAFF, "Send " + STAFF_HEADER);
        }
        return new Viewer(ParticipantType.ADMIN, staffId);
    }

    public static Sender sender(Viewer viewer) {
        return viewer.staff() ? Sender.staff(viewer.id()) : Sender.user(viewer.id());
    }

    private static Integer id(String value, String header) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(header + " must be a number");
        }
    }
}
