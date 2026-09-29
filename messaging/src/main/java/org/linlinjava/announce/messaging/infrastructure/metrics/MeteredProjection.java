package org.linlinjava.announce.messaging.infrastructure.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.cqrs.readside.EventEnvelope;
import org.linlinjava.announce.cqrs.readside.ProjectionHandlers;
import org.linlinjava.announce.cqrs.readside.ReactiveEventHandler;
import org.linlinjava.announce.cqrs.readside.ReactiveProjection;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Wraps a {@link ReactiveProjection} so that every handler call is timed: {@code messaging.projection.handler}
 * (the handler itself) and {@code messaging.projection.delay} (from the event's write time to the end of the
 * handler, which is what the group's checkpoint is about to record). The framework sees only this wrapper;
 * {@link #group()}, {@link #prepare()} and {@link #reset()} pass through.
 *
 * <p>The registry is resolved on the first event, not at wrap time, because the wrapper is created by a bean
 * post-processor before the metrics auto-configuration has necessarily run.
 */
public final class MeteredProjection implements ReactiveProjection {

    private final ReactiveProjection delegate;
    private final Supplier<MeterRegistry> registry;
    private final Clock clock;

    public MeteredProjection(ReactiveProjection delegate, Supplier<MeterRegistry> registry, Clock clock) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ReactiveProjection delegate() {
        return delegate;
    }

    @Override
    public String group() {
        return delegate.group();
    }

    @Override
    public void configure(ProjectionHandlers handlers) {
        ProjectionHandlers own = new ProjectionHandlers();
        delegate.configure(own);
        for (Class<?> eventClass : own.eventClasses()) {
            ReactiveEventHandler<Object> handler = own.find(eventClass);
            handlers.on(eventClass, (event, envelope) -> timed(handler, event, envelope));
        }
    }

    @Override
    public Mono<Void> prepare() {
        return delegate.prepare();
    }

    @Override
    public Mono<Void> reset() {
        return delegate.reset();
    }

    private Mono<Void> timed(ReactiveEventHandler<Object> handler, Object event, EventEnvelope envelope) {
        return Mono.defer(() -> {
            Instant started = clock.instant();
            return handler.handle(event, envelope)
                    .doOnSuccess(v -> record(envelope, started, MessagingMeters.OUTCOME_OK))
                    .doOnError(e -> record(envelope, started, e instanceof CommandRejectedException
                            ? MessagingMeters.OUTCOME_REJECTED : MessagingMeters.OUTCOME_ERROR));
        });
    }

    private void record(EventEnvelope envelope, Instant started, String outcome) {
        MeterRegistry meters = registry.get();
        if (meters == null) {
            return;
        }
        Instant now = clock.instant();
        MessagingMeters.projectionHandler(meters, group(), outcome).record(Duration.between(started, now));
        if (envelope.time() != null) {
            MessagingMeters.projectionDelay(meters, group()).record(MessagingMeters.nonNegative(Duration.between(envelope.time(), now)));
        }
    }
}
