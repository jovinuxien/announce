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
 * names none). The client sends nothing but keep-alives, which are ignored.
 */
@Component
public class StaffWebSocketHandler implements WebSocketHandler {

    private final LiveSessions sessions;

    public StaffWebSocketHandler(LiveSessions sessions) {
        this.sessions = sessions;
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
        Mono<Void> outbound = session.send(staffSession.sink().frames().map(session::textMessage));
        Mono<Void> inbound = session.receive().then().doFinally(signal -> staffSession.sink().complete());
        return Mono.when(outbound, inbound).doFinally(signal -> sessions.detachStaff(staffSession));
    }
}
