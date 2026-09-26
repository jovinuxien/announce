package org.linlinjava.announce.messaging.infrastructure.live;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The WebSocket sessions open on this node (book: ChatServer's client and CSR sessions): per conversation, and the
 * staff dashboard sessions with their client type. Runtime state only; the live topic {@code messaging.live}
 * reaches every node so each one pushes to the sessions it holds.
 */
@Component
public class LiveSessions {

    public record StaffSession(String clientType, LiveSink sink) {
    }

    private final Map<String, Set<LiveSink>> byConversation = new ConcurrentHashMap<>();
    private final Set<StaffSession> staff = ConcurrentHashMap.newKeySet();

    public LiveSink attach(String conversationId) {
        LiveSink sink = new LiveSink();
        byConversation.computeIfAbsent(conversationId, id -> ConcurrentHashMap.newKeySet()).add(sink);
        return sink;
    }

    public void detach(String conversationId, LiveSink sink) {
        sink.complete();
        byConversation.computeIfPresent(conversationId, (id, sinks) -> {
            sinks.remove(sink);
            return sinks.isEmpty() ? null : sinks;
        });
    }

    public StaffSession attachStaff(String clientType) {
        StaffSession session = new StaffSession(clientType == null || clientType.isBlank() ? null : clientType, new LiveSink());
        staff.add(session);
        return session;
    }

    public void detachStaff(StaffSession session) {
        session.sink().complete();
        staff.remove(session);
    }

    /** Pushes a frame to every session of the conversation on this node. */
    public void toConversation(String conversationId, String json) {
        Set<LiveSink> sinks = byConversation.get(conversationId);
        if (sinks != null) {
            sinks.forEach(sink -> sink.emit(json));
        }
    }

    /** Pushes a frame to the staff sessions of {@code clientType}; null reaches every staff session. */
    public void toStaff(String clientType, String json) {
        staff.stream()
                .filter(s -> clientType == null || s.clientType() == null || clientType.equalsIgnoreCase(s.clientType()))
                .forEach(s -> s.sink().emit(json));
    }

    public int sessions(String conversationId) {
        Set<LiveSink> sinks = byConversation.get(conversationId);
        return sinks == null ? 0 : sinks.size();
    }

    public int staffSessions() {
        return staff.size();
    }
}
