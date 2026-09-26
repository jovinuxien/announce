package org.linlinjava.announce.messaging.interfaces.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.messaging.application.ConversationQueries;
import org.linlinjava.announce.messaging.application.ConversationQueries.Viewer;
import org.linlinjava.announce.messaging.application.Conversations;
import org.linlinjava.announce.messaging.domain.conversation.ContentType;
import org.linlinjava.announce.messaging.domain.conversation.Sender;
import org.linlinjava.announce.messaging.infrastructure.live.LiveFrames;
import org.linlinjava.announce.messaging.infrastructure.live.LiveSessions;
import org.linlinjava.announce.messaging.infrastructure.live.LiveSink;
import org.linlinjava.announce.messaging.interfaces.Principals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * {@code /ws/conversations/{id}}: the live channel of one conversation. The handshake identifies the member or
 * staff member by {@code X-User-Id} / {@code X-Staff-Id}; a caller who is neither a participant nor staff is closed
 * with 4403 (4401 without a principal). Frames the client sends are commands ({@code post}, {@code offer},
 * {@code accept-offer}, {@code decline-offer}, {@code read}, {@code close}, {@code ping}) answered with {@code ack}
 * or {@code error}; what every participant sees ({@code message}, {@code status}) arrives through the live topic,
 * on this node and every other one.
 */
@Component
public class ConversationWebSocketHandler implements WebSocketHandler {

    public static final int NOT_AUTHENTICATED = 4401;
    public static final int NOT_PARTICIPANT = 4403;

    private static final Logger log = LoggerFactory.getLogger(ConversationWebSocketHandler.class);

    /** What a client sends; {@code type} decides which other fields matter. */
    public record ClientFrame(String type, String clientMsgId, String contentType, String content, String attachmentUrl,
                              Long amountCents, String currency, Integer seq) {
    }

    private final Conversations conversations;
    private final ConversationQueries queries;
    private final LiveSessions sessions;
    private final LiveFrames frames;
    private final ObjectMapper objectMapper;

    public ConversationWebSocketHandler(Conversations conversations, ConversationQueries queries, LiveSessions sessions,
                                        LiveFrames frames, ObjectMapper objectMapper) {
        this.conversations = conversations;
        this.queries = queries;
        this.sessions = sessions;
        this.frames = frames;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        HandshakeInfo handshake = session.getHandshakeInfo();
        String conversationId = lastSegment(handshake.getUri().getPath());
        Viewer viewer;
        try {
            viewer = Principals.viewer(handshake.getHeaders());
        } catch (CommandRejectedException | IllegalArgumentException e) {
            return session.close(new CloseStatus(NOT_AUTHENTICATED, e.getMessage()));
        }
        return queries.mayAttach(conversationId, viewer)
                .flatMap(allowed -> allowed ? attach(session, conversationId, viewer)
                        : session.close(new CloseStatus(NOT_PARTICIPANT, "Not a participant of conversation " + conversationId)));
    }

    private Mono<Void> attach(WebSocketSession session, String conversationId, Viewer viewer) {
        LiveSink sink = sessions.attach(conversationId);
        Sender sender = Principals.sender(viewer);
        Mono<Void> outbound = session.send(sink.frames().map(session::textMessage));
        Mono<Void> inbound = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .concatMap(text -> handleFrame(conversationId, viewer, sender, text, sink))
                .then()
                .doFinally(signal -> sink.complete());
        return Mono.when(outbound, inbound).doFinally(signal -> sessions.detach(conversationId, sink));
    }

    private Mono<Void> handleFrame(String conversationId, Viewer viewer, Sender sender, String text, LiveSink sink) {
        ClientFrame frame;
        try {
            frame = objectMapper.readValue(text, ClientFrame.class);
        } catch (Exception e) {
            sink.emit(frames.error(null, null, 400, "bad-frame", "Not a valid frame: " + e.getMessage()));
            return Mono.empty();
        }
        String action = frame.type() == null ? "" : frame.type();
        Mono<String> answer = switch (action) {
            case "post" -> conversations.post(conversationId, sender, contentType(frame), frame.content(), frame.attachmentUrl(), null, null,
                    frame.clientMsgId(), null).map(result -> frames.ack(action, result));
            case "offer" -> conversations.post(conversationId, sender, ContentType.OFFER, frame.content(), null, frame.amountCents(),
                    frame.currency(), frame.clientMsgId(), null).map(result -> frames.ack(action, result));
            case "accept-offer" -> conversations.acceptOffer(conversationId, viewer.id(), null).map(c -> frames.ack(action, null));
            case "decline-offer" -> conversations.declineOffer(conversationId, viewer.id(), null).map(c -> frames.ack(action, null));
            case "read" -> conversations.markRead(conversationId, viewer.type(), viewer.id(), frame.seq() == null ? 0 : frame.seq())
                    .thenReturn(frames.ack(action, null));
            case "close" -> conversations.close(conversationId, sender, null).map(c -> frames.ack(action, null));
            case "ping" -> Mono.just(frames.pong());
            default -> Mono.just(frames.error(action, frame.clientMsgId(), 400, "unknown-frame", "Unknown frame type " + action));
        };
        return answer.onErrorResume(e -> Mono.just(error(action, frame.clientMsgId(), e))).doOnNext(sink::emit).then();
    }

    private String error(String action, String clientMsgId, Throwable e) {
        if (e instanceof CommandRejectedException rejected) {
            return frames.error(action, clientMsgId, rejected.getStatus().value(), rejected.getCode(), rejected.getMessage());
        }
        if (e instanceof IllegalArgumentException) {
            return frames.error(action, clientMsgId, 400, "bad-request", e.getMessage());
        }
        if (e instanceof com.opencqrs.framework.command.CommandSubjectDoesNotExistException) {
            return frames.error(action, clientMsgId, 404, "conversation-not-found", e.getMessage());
        }
        log.warn("WebSocket frame {} failed", action, e);
        return frames.error(action, clientMsgId, 500, "internal", "The frame could not be handled");
    }

    private static ContentType contentType(ClientFrame frame) {
        return Optional.ofNullable(frame.contentType()).map(String::toUpperCase).map(ContentType::valueOf).orElse(ContentType.TEXT);
    }

    static String lastSegment(String path) {
        String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        return trimmed.substring(trimmed.lastIndexOf('/') + 1);
    }
}
