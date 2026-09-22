package org.linlinjava.announce.cqrs.readside;

import org.linlinjava.announce.cqrs.observability.CqrsTracing;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared collaborators of every {@link ReactiveProjectionAdapter}: tracing, the handler timeout, and the
 * once-only {@link ReactiveProjection#prepare()} call.
 */
public class ReactiveProjectionSupport {

    public static final String BEAN_NAME = "reactiveProjectionSupport";

    private final CqrsTracing tracing;
    private final Duration handlerTimeout;
    private final Set<ReactiveProjection> prepared = ConcurrentHashMap.newKeySet();

    public ReactiveProjectionSupport(CqrsTracing tracing, Duration handlerTimeout) {
        this.tracing = Objects.requireNonNull(tracing, "tracing");
        this.handlerTimeout = Objects.requireNonNull(handlerTimeout, "handlerTimeout");
    }

    public CqrsTracing tracing() {
        return tracing;
    }

    public Duration handlerTimeout() {
        return handlerTimeout;
    }

    /** Runs {@link ReactiveProjection#prepare()} the first time a projection is seen. */
    public void prepare(ReactiveProjection projection) {
        if (prepared.add(projection)) {
            projection.prepare().block(handlerTimeout);
        }
    }
}
