package org.linlinjava.announce.cqrs.readside;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.eventhandler.EventHandler;
import com.opencqrs.framework.eventhandler.EventHandlerDefinition;
import org.linlinjava.announce.cqrs.observability.CqrsTracing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;

/**
 * Bridges a {@link ReactiveProjection} to OpenCQRS event handling.
 *
 * <p>One {@link EventHandlerDefinition} on {@code Object} is registered per projection, so the group's
 * processor delivers every event of its fetch subject here; the adapter dispatches by event class and skips
 * unhandled types. OpenCQRS processors run on their own threads (never on the event loop), call handlers
 * synchronously and checkpoint after the call returns, so the handler's {@code Mono} is awaited here with the
 * configured timeout. An error or a timeout propagates to the processor, which retries with back-off and
 * does not advance the checkpoint.
 */
public class ReactiveProjectionAdapter implements EventHandler.ForObjectAndMetaDataAndRawEvent<Object> {

    public static final String SPAN_NAME = "cqrs.projection";

    private static final Logger log = LoggerFactory.getLogger(ReactiveProjectionAdapter.class);

    private final ReactiveProjection projection;
    private final ProjectionHandlers handlers;
    private final ReactiveProjectionSupport support;

    public ReactiveProjectionAdapter(ReactiveProjection projection, ReactiveProjectionSupport support) {
        this.projection = Objects.requireNonNull(projection, "projection");
        this.support = Objects.requireNonNull(support, "support");
        this.handlers = new ProjectionHandlers();
        projection.configure(handlers);
        if (handlers.isEmpty()) {
            log.warn("Projection {} ({}) registered no handlers", projection.group(), projection.getClass().getName());
        }
    }

    /** Factory used by {@link ReactiveProjectionRegistrar}; prepares the read model before the group starts. */
    public static EventHandlerDefinition<Object> definition(ReactiveProjection projection, ReactiveProjectionSupport support) {
        support.prepare(projection);
        return new EventHandlerDefinition<>(projection.group(), Object.class, new ReactiveProjectionAdapter(projection, support));
    }

    public ReactiveProjection projection() {
        return projection;
    }

    public ProjectionHandlers handlers() {
        return handlers;
    }

    @Override
    public void handle(Object event, Map<String, ?> metaData, Event raw) {
        apply(event, EventEnvelope.of(raw, metaData)).block(support.handlerTimeout());
    }

    /** The reactive pipeline for one event, without blocking; used by the test harness. */
    public Mono<Void> apply(Object event, EventEnvelope envelope) {
        ReactiveEventHandler<Object> handler = handlers.find(event.getClass());
        if (handler == null) {
            return Mono.empty();
        }
        Mono<Void> work = Mono.defer(() -> handler.handle(event, envelope));
        return support.tracing().traced(SPAN_NAME, Map.of(
                        CqrsTracing.TAG_GROUP, projection.group(),
                        CqrsTracing.TAG_SUBJECT, envelope.subject(),
                        CqrsTracing.TAG_EVENT_TYPE, envelope.type()),
                envelope.correlationId().orElse(null), null, work);
    }
}
