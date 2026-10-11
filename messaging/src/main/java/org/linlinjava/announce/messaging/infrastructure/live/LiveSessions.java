package org.linlinjava.announce.messaging.infrastructure.live;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.linlinjava.announce.messaging.application.MessagingProperties;
import org.linlinjava.announce.messaging.infrastructure.metrics.MessagingMeters;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * The WebSocket sessions open on this node (book: ChatServer's client and CSR sessions): per conversation, and the
 * staff dashboard sessions with their client type. Runtime state only; the live topic {@code messaging.live}
 * reaches every node so each one pushes to the sessions it holds.
 *
 * <p>Exposes the node's socket population and buffer depth as gauges ({@code messaging.sessions},
 * {@code messaging.sessions.conversations}, {@code messaging.staff_sessions}, {@code messaging.sink.buffered},
 * {@code messaging.sink.buffered.max}) and counts refused emissions ({@code messaging.sink.dropped}). Every sink
 * is bounded by {@code messaging.socket.buffer-frames}.
 */
@Component
public class LiveSessions {

    public record StaffSession(String clientType, LiveSink sink) {
    }

    private final Map<String, Set<LiveSink>> byConversation = new ConcurrentHashMap<>();
    private final Set<StaffSession> staff = ConcurrentHashMap.newKeySet();
    private final AtomicInteger conversationSockets = new AtomicInteger();
    private final MeterRegistry meters;
    private final int bufferFrames;

    public LiveSessions(MeterRegistry meters, MessagingProperties properties) {
        this.meters = meters;
        this.bufferFrames = properties.getSocket().getBufferFrames();
        Gauge.builder(MessagingMeters.SESSIONS, conversationSockets, AtomicInteger::get)
                .description("Conversation WebSocket sessions open on this node").register(meters);
        Gauge.builder(MessagingMeters.SESSION_CONVERSATIONS, byConversation, Map::size)
                .description("Conversations with at least one socket on this node").register(meters);
        Gauge.builder(MessagingMeters.STAFF_SESSIONS, staff, Set::size)
                .description("Staff dashboard sessions open on this node").register(meters);
        Gauge.builder(MessagingMeters.SINK_BUFFERED, this, s -> s.sinks().mapToInt(LiveSink::buffered).sum())
                .description("Frames waiting in the outbound buffers of all sockets on this node").register(meters);
        Gauge.builder(MessagingMeters.SINK_BUFFERED_MAX, this, s -> s.sinks().mapToInt(LiveSink::buffered).max().orElse(0))
                .description("Deepest outbound buffer of one socket on this node").register(meters);
    }

    public LiveSink attach(String conversationId) {
        LiveSink sink = newSink();
        byConversation.computeIfAbsent(conversationId, id -> ConcurrentHashMap.newKeySet()).add(sink);
        conversationSockets.incrementAndGet();
        return sink;
    }

    public void detach(String conversationId, LiveSink sink) {
        sink.complete();
        byConversation.computeIfPresent(conversationId, (id, sinks) -> {
            if (sinks.remove(sink)) {
                conversationSockets.decrementAndGet();
            }
            return sinks.isEmpty() ? null : sinks;
        });
    }

    public StaffSession attachStaff(String clientType) {
        StaffSession session = new StaffSession(clientType == null || clientType.isBlank() ? null : clientType, newSink());
        staff.add(session);
        return session;
    }

    public void detachStaff(StaffSession session) {
        session.sink().complete();
        staff.remove(session);
    }

    /** Pushes a frame to every session of the conversation on this node; returns how many received it. */
    public int toConversation(String conversationId, String json) {
        Set<LiveSink> sinks = byConversation.get(conversationId);
        if (sinks == null) {
            return 0;
        }
        int pushed = 0;
        for (LiveSink sink : sinks) {
            sink.emit(json);
            pushed++;
        }
        return pushed;
    }

    /** Pushes a frame to the staff sessions of {@code clientType} (null reaches every one); returns how many received it. */
    public int toStaff(String clientType, String json) {
        int pushed = 0;
        for (StaffSession s : staff) {
            if (clientType == null || s.clientType() == null || clientType.equalsIgnoreCase(s.clientType())) {
                s.sink().emit(json);
                pushed++;
            }
        }
        return pushed;
    }

    /** The sinks of the conversation's sockets on this node, for inspection. */
    public List<LiveSink> sinksOf(String conversationId) {
        Set<LiveSink> sinks = byConversation.get(conversationId);
        return sinks == null ? List.of() : List.copyOf(sinks);
    }

    public int sessions(String conversationId) {
        Set<LiveSink> sinks = byConversation.get(conversationId);
        return sinks == null ? 0 : sinks.size();
    }

    public int staffSessions() {
        return staff.size();
    }

    private LiveSink newSink() {
        return new LiveSink(bufferFrames, result -> meters.counter(MessagingMeters.SINK_DROPPED, MessagingMeters.TAG_REASON, result.name()).increment());
    }

    private Stream<LiveSink> sinks() {
        return Stream.concat(byConversation.values().stream().flatMap(Collection::stream), staff.stream().map(StaffSession::sink));
    }
}
