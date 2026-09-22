package org.linlinjava.announce.cqrs.readside;

import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The handlers a {@link ReactiveProjection} registers in {@link ReactiveProjection#configure}.
 * Events whose class matches no registration are skipped (their checkpoint still advances).
 */
public final class ProjectionHandlers {

    private final Map<Class<?>, ReactiveEventHandler<Object>> handlers = new LinkedHashMap<>();

    /** Registers a handler for {@code eventClass} and its subclasses. */
    @SuppressWarnings("unchecked")
    public <E> ProjectionHandlers on(Class<E> eventClass, ReactiveEventHandler<? super E> handler) {
        Objects.requireNonNull(eventClass, "eventClass");
        Objects.requireNonNull(handler, "handler");
        if (handlers.putIfAbsent(eventClass, (ReactiveEventHandler<Object>) handler) != null) {
            throw new IllegalArgumentException("A handler for " + eventClass.getName() + " is already registered");
        }
        return this;
    }

    /** Registers a handler that ignores the envelope. */
    public <E> ProjectionHandlers on(Class<E> eventClass, java.util.function.Function<? super E, Mono<Void>> handler) {
        Objects.requireNonNull(handler, "handler");
        return on(eventClass, (event, envelope) -> handler.apply(event));
    }

    /** The most specific handler for {@code eventClass}: exact match first, then the first assignable registration. */
    public ReactiveEventHandler<Object> find(Class<?> eventClass) {
        ReactiveEventHandler<Object> exact = handlers.get(eventClass);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<Class<?>, ReactiveEventHandler<Object>> entry : handlers.entrySet()) {
            if (entry.getKey().isAssignableFrom(eventClass)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public Set<Class<?>> eventClasses() {
        return Set.copyOf(handlers.keySet());
    }

    public boolean isEmpty() {
        return handlers.isEmpty();
    }
}
