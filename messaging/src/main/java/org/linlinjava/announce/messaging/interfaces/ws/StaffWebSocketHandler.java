package org.linlinjava.announce.messaging.interfaces.ws;

import org.linlinjava.announce.messaging.infrastructure.live.LiveSessions;
import org.linlinjava.announce.messaging.interfaces.Principals;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 * {@code /ws/staff?clientType=desktop}: the staff dashboard channel (book: CSR sessions and the bulletin board).
 * Needs {@code X-Staff-Id}; receives {@code bulletin} frames for its client type (or every type when the bulletin
 * names none). The client sends nothing but keep-alives, which are ignored. Pings, idle and slow-consumer closes:
 * {@link SocketLifecycle}.
 */
@Component
public class StaffWebSocketHandler implements WebSocketHandler {

    private final LiveSessions sessions;
    private final SocketLifecycle lifecycle;

    public StaffWebSocketHandler(LiveSessions sessions, SocketLifecycle lifecycle) {
        this.sessions = sessions;
        this.lifecycle = lifecycle;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        HandshakeInfo handshake = session.getHandshakeInfo();
        String staff = handshake.getHeaders().getFirst(Principals.STAFF_HEADER);
        if (staff == null || staff.isBlank()) {
            return session.close(new CloseStatus(ConversationWebSocketHandler.NOT_AUTHENTICATED, "Send " + Principals.STAFF_HEADER));
        }
        String clientType = UriComponentsBuilder.fromUri(handshake.getUri()).build().getQueryParams().getFirst("clientType");
        LiveSessions.StaffSession staffSession = sessions.attachStaff(clientType);
        return lifecycle.run(session, staffSession.sink(), text -> Mono.empty())
                .doFinally(signal -> sessions.detachStaff(staffSession));
    }
}
