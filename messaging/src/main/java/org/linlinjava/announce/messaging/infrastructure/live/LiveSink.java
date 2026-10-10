package org.linlinjava.announce.messaging.infrastructure.live;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

import java.util.Queue;
import java.util.function.Consumer;

/**
 * The outbound side of one WebSocket session: JSON frames pushed from the live topic and from the session's own
 * command results. Emission is serialised because both arrive on different threads.
 *
 * <p>The buffer is unbounded for now (tuning step 5 of {@code doc/messaging-architecture.md} bounds it); its
 * depth is readable through {@link #buffered()} and a refused emission is reported to the owner so that it
 * can be counted.
 */
public final class LiveSink {

    private final Queue<String> queue = Queues.<String>unbounded().get();
    private final Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer(queue);
    private final Consumer<Sinks.EmitResult> onRefused;

    public LiveSink() {
        this(result -> {
        });
    }

    /** @param onRefused called with the result of an emission the sink did not accept (never for OK) */
    public LiveSink(Consumer<Sinks.EmitResult> onRefused) {
        this.onRefused = onRefused;
    }

    public synchronized void emit(String json) {
        Sinks.EmitResult result = sink.tryEmitNext(json);
        if (result.isFailure()) {
            onRefused.accept(result);
        }
    }

    public synchronized void complete() {
        sink.tryEmitComplete();
    }

    public Flux<String> frames() {
        return sink.asFlux();
    }

    /** Frames accepted but not yet taken by the WebSocket send. */
    public int buffered() {
        return queue.size();
    }
}
