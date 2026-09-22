package org.linlinjava.announce.cqrs.test;

import org.linlinjava.announce.cqrs.observability.CqrsTracing;
import org.linlinjava.announce.cqrs.observability.MetaDataKeys;
import org.linlinjava.announce.cqrs.readside.EventEnvelope;
import org.linlinjava.announce.cqrs.readside.ReactiveProjection;
import org.linlinjava.announce.cqrs.readside.ReactiveProjectionAdapter;
import org.linlinjava.announce.cqrs.readside.ReactiveProjectionSupport;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Feeds events through a {@link ReactiveProjection} the way the event handling processor would, without
 * EventSourcingDB: the {@code ReadSideTestDriver} of this framework.
 *
 * <pre>{@code
 * ProjectionHarness harness = ProjectionHarness.of(new ListingSummaryProjection(databaseClient)).prepare();
 * harness.feed("/listing/1", new ListingCreated("Bike", 12000))
 *        .feed("/listing/1", new ListingPriceChanged(9900));
 * // assert on the read model
 * }</pre>
 */
public final class ProjectionHarness {

    private final ReactiveProjection projection;
    private final ReactiveProjectionAdapter adapter;
    private final Duration timeout;
    private final AtomicLong sequence = new AtomicLong();
    private final List<EventEnvelope> handled = new ArrayList<>();

    private ProjectionHarness(ReactiveProjection projection, Duration timeout) {
        this.projection = projection;
        this.timeout = timeout;
        this.adapter = new ReactiveProjectionAdapter(projection, new ReactiveProjectionSupport(CqrsTracing.disabled(), timeout));
    }

    public static ProjectionHarness of(ReactiveProjection projection) {
        return new ProjectionHarness(projection, Duration.ofSeconds(10));
    }

    public static ProjectionHarness of(ReactiveProjection projection, Duration timeout) {
        return new ProjectionHarness(projection, timeout);
    }

    public ReactiveProjection projection() {
        return projection;
    }

    /** Runs {@link ReactiveProjection#prepare()}. */
    public ProjectionHarness prepare() {
        projection.prepare().block(timeout);
        return this;
    }

    /** Runs {@link ReactiveProjection#reset()}. */
    public ProjectionHarness reset() {
        projection.reset().block(timeout);
        return this;
    }

    public ProjectionHarness feed(String subject, Object event) {
        return feed(subject, event, Map.of());
    }

    public ProjectionHarness feed(String subject, Object event, Map<String, ?> metaData) {
        apply(subject, event, metaData).block(timeout);
        return this;
    }

    /** The non-blocking variant of {@link #feed}, for {@code StepVerifier} style tests. */
    public Mono<Void> apply(String subject, Object event, Map<String, ?> metaData) {
        EventEnvelope envelope = new EventEnvelope(subject, event.getClass().getSimpleName(),
                String.valueOf(sequence.incrementAndGet()), Instant.now(), withCorrelation(metaData));
        return adapter.apply(event, envelope).doOnSuccess(ignored -> handled.add(envelope));
    }

    /** The envelopes of every event fed so far, whether or not the projection handled its type. */
    public List<EventEnvelope> handled() {
        return List.copyOf(handled);
    }

    private static Map<String, ?> withCorrelation(Map<String, ?> metaData) {
        if (metaData.containsKey(MetaDataKeys.CORRELATION_ID)) {
            return metaData;
        }
        Map<String, Object> copy = new java.util.LinkedHashMap<>(metaData);
        copy.put(MetaDataKeys.CORRELATION_ID, "test-correlation");
        return copy;
    }
}
