package org.linlinjava.announce.cqrs.observability;

import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.Optional;
import java.util.UUID;

/**
 * Carries the correlation id through the Reactor context of a WebFlux request, a command dispatch,
 * a projection handler or a subscriber handler.
 */
public final class CorrelationContext {

    public static final String KEY = "cqrs.correlationId";

    private CorrelationContext() {
    }

    public static Context with(Context context, String correlationId) {
        return correlationId == null ? context : context.put(KEY, correlationId);
    }

    public static Optional<String> correlationId(ContextView view) {
        return view.getOrEmpty(KEY);
    }

    public static String newCorrelationId() {
        return UUID.randomUUID().toString();
    }
}
