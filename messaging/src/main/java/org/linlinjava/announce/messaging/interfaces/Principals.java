package org.linlinjava.announce.messaging.interfaces;

import org.linlinjava.announce.cqrs.security.Caller;
import org.linlinjava.announce.cqrs.security.Callers;
import org.linlinjava.announce.messaging.application.ConversationQueries.Viewer;
import org.linlinjava.announce.messaging.domain.conversation.ParticipantType;
import org.linlinjava.announce.messaging.domain.conversation.Sender;
import org.springframework.http.HttpHeaders;

/**
 * Who is calling, in this context's terms: the framework's {@link Callers} reads {@code X-User-Id} and
 * {@code X-User-Type} ({@code MEMBER} from the customer edge, {@code ADMIN} from the admin edge) and this class
 * turns them into a {@link Viewer} / {@link Sender}. The headers can be trusted because the framework's
 * {@code ServiceTokenWebFilter} refuses every request and WebSocket handshake that did not pass an edge, and the
 * edges strip whatever a client sent under these names. Writes may carry {@code X-Command-Id} for idempotent
 * dispatch.
 */
public final class Principals {

    public static final String USER_HEADER = Callers.USER_HEADER;
    public static final String TYPE_HEADER = Callers.TYPE_HEADER;
    public static final String COMMAND_ID_HEADER = "X-Command-Id";
    public static final String NO_PRINCIPAL = Callers.NO_PRINCIPAL;
    public static final String NOT_STAFF = Callers.NOT_STAFF;

    private Principals() {
    }

    /** The member or staff member behind the headers; 401 {@code no-principal} without an identity. */
    public static Viewer viewer(HttpHeaders headers) {
        Caller caller = Callers.any(headers);
        return new Viewer(caller.staff() ? ParticipantType.ADMIN : ParticipantType.USER, caller.id());
    }

    /** The member behind a member-only route: 401 without an identity, 403 for staff. */
    public static Viewer user(HttpHeaders headers) {
        return new Viewer(ParticipantType.USER, Callers.member(headers));
    }

    /** The staff member behind a staff-only route or handshake: 401 without an identity, 403 for a member. */
    public static Integer staff(HttpHeaders headers) {
        return Callers.staff(headers);
    }

    public static Sender sender(Viewer viewer) {
        return viewer.staff() ? Sender.staff(viewer.id()) : Sender.user(viewer.id());
    }
}
