package org.linlinjava.announce.messaging.infrastructure.live;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The outbound side of one WebSocket session: JSON frames pushed from the live topic and from the session's own
 * command results. Emission is serialised because both arrive on different threads.
 *
 * <p>The queue holds at most {@code capacity} frames (rounded up to a power of two; 0 for no bound). The WebSocket
 * send takes frames as fast as the client's connection accepts them, so the queue only fills when the client
 * stops reading. The first frame that does not fit marks the sink as {@linkplain #overflowed() overflowed} and
 * completes it: what it holds is still delivered, nothing else is queued, and the owner closes the socket once
 * the send has drained (code 4408, see {@code interfaces.ws.SocketLifecycle}). Every refused frame is reported
 * to the owner so it can be counted ({@code messaging.sink.dropped}).
 */
public final class LiveSink {

    private final int capacity;
    private final Queue<String> queue;
    private final Sinks.Many<String> sink;
    private final Consumer<Sinks.EmitResult> onRefused;
    private volatile boolean overflowed;
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong emitted = new AtomicLong();

    /** Unbounded, drops uncounted; for tests. */
    public LiveSink() {
        this(0, result -> {
        });
    }

    /**
     * @param capacity  frames queued before the sink overflows; 0 for no bound
     * @param onRefused called with the result of an emission the sink did not accept (never for OK)
     */
    public LiveSink(int capacity, Consumer<Sinks.EmitResult> onRefused) {
        this.capacity = capacity;
        this.queue = capacity <= 0 ? Queues.<String>unbounded().get() : Queues.<String>get(capacity).get();
        this.sink = Sinks.many().unicast().onBackpressureBuffer(queue);
        this.onRefused = onRefused;
    }

    public synchronized void emit(String json) {
        if (overflowed) {
            onRefused.accept(Sinks.EmitResult.FAIL_OVERFLOW);
            return;
        }
        Sinks.EmitResult result = sink.tryEmitNext(json);
        if (result == Sinks.EmitResult.FAIL_OVERFLOW) {
            overflowed = true;
            sink.tryEmitComplete();
        }
        if (result.isFailure()) {
            onRefused.accept(result);
        } else {
            accepted.incrementAndGet();
        }
    }

    public synchronized void complete() {
        sink.tryEmitComplete();
    }

    public Flux<String> frames() {
        return sink.asFlux().doOnNext(frame -> emitted.incrementAndGet());
    }

    /** Frames the queue took since the sink was created (the WebSocket send has taken {@link #emitted()} of them). */
    public long accepted() {
        return accepted.get();
    }

    public long emitted() {
        return emitted.get();
    }

    /** Frames accepted but not yet taken by the WebSocket send. */
    public int buffered() {
        return queue.size();
    }

    /** The configured bound, 0 when unbounded. */
    public int capacity() {
        return capacity;
    }

    /** True once a frame was refused for lack of room: the client is a slow consumer and the socket is to be closed. */
    public boolean overflowed() {
        return overflowed;
    }
}
