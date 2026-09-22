package org.linlinjava.announce.cqrs.readside;

import reactor.core.publisher.Mono;

/**
 * Handles one event on the read side. The returned {@code Mono} completes when the read model was updated;
 * the processor stores the checkpoint only after that, so an error or a timeout makes the event be retried
 * (at-least-once: handlers must be idempotent, for example upserts keyed by the subject).
 */
@FunctionalInterface
public interface ReactiveEventHandler<E> {

    Mono<Void> handle(E event, EventEnvelope envelope);
}
