package org.linlinjava.announce.messaging.infrastructure.live;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * The outbound side of one WebSocket session: JSON frames pushed from the live topic and from the session's own
 * command results. Emission is serialised because both arrive on different threads.
 */
public final class LiveSink {

    private final Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

    public synchronized void emit(String json) {
        sink.tryEmitNext(json);
    }

    public synchronized void complete() {
        sink.tryEmitComplete();
    }

    public Flux<String> frames() {
        return sink.asFlux();
    }
}
