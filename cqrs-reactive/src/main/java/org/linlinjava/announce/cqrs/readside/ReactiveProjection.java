package org.linlinjava.announce.cqrs.readside;

import reactor.core.publisher.Mono;

/**
 * A read-side processor in the Lagom sense: it consumes the events of one event handling group in order,
 * updates a read model and lets the framework checkpoint its progress next to that read model.
 *
 * <p>Declare it as a Spring bean; the framework registers it with OpenCQRS under {@link #group()} and the
 * group's fetch subject, sequencing and retry settings come from
 * {@code opencqrs.event-handling.groups.<group>.*} (falling back to {@code opencqrs.event-handling.standard.*}).
 *
 * <pre>{@code
 * @Component
 * class AnnounceSummaryProjection implements ReactiveProjection {
 *     public String group() { return "announce-summary"; }
 *     public void configure(ProjectionHandlers handlers) {
 *         handlers.on(AnnouncePublished.class, (event, envelope) -> upsert(envelope.subject(), event));
 *     }
 *     public Mono<Void> prepare() { return createTableIfMissing(); }
 *     public Mono<Void> reset() { return truncate(); }
 * }
 * }</pre>
 */
public interface ReactiveProjection {

    /** The event handling group id; one processor (per partition) runs per group. */
    String group();

    /** Registers the event handlers of this projection. Called once at start-up. */
    void configure(ProjectionHandlers handlers);

    /**
     * Prepares the read model before the group starts, typically creating its tables (Lagom's
     * {@code globalPrepare}). Must be idempotent.
     */
    default Mono<Void> prepare() {
        return Mono.empty();
    }

    /**
     * Clears the read model so that a replay from the beginning of the log rebuilds it. Used by
     * {@link ProjectionRebuilder}.
     */
    default Mono<Void> reset() {
        return Mono.empty();
    }
}
