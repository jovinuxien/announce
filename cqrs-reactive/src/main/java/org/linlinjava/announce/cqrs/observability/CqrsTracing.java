package org.linlinjava.announce.cqrs.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Wraps read-side and subscriber handlers in a span and carries trace context through Kafka headers.
 * Every method degrades to a no-op when no {@link Tracer} is configured.
 */
public class CqrsTracing {

    public static final String TAG_GROUP = "cqrs.group";
    public static final String TAG_SUBJECT = "cqrs.subject";
    public static final String TAG_EVENT_TYPE = "cqrs.event.type";
    public static final String TAG_CORRELATION_ID = "cqrs.correlation.id";

    private final Tracer tracer;
    private final Propagator propagator;

    public CqrsTracing(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    public static CqrsTracing disabled() {
        return new CqrsTracing(null, null);
    }

    /**
     * Runs {@code work} inside a new span named {@code spanName}, tagged with the given values, and with the
     * correlation id in the Reactor context. The span is a child of {@code parent} when given.
     */
    public <T> Mono<T> traced(String spanName, Map<String, String> tags, String correlationId, Span parent, Mono<T> work) {
        Mono<T> withContext = work.contextWrite(context -> CorrelationContext.with(context, correlationId));
        if (tracer == null) {
            return withContext;
        }
        return Mono.using(
                () -> {
                    Span span = (parent != null ? tracer.nextSpan(parent) : tracer.nextSpan()).name(spanName);
                    tags.forEach((key, value) -> {
                        if (value != null) {
                            span.tag(key, value);
                        }
                    });
                    if (correlationId != null) {
                        span.tag(TAG_CORRELATION_ID, correlationId);
                    }
                    return span.start();
                },
                span -> Mono.defer(() -> {
                    try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
                        return withContext;
                    }
                }).doOnError(span::error),
                Span::end);
    }

    /** Writes the current trace context (for example {@code traceparent}) into the given header sink. */
    public void inject(BiConsumer<String, String> headerSink) {
        if (tracer == null || propagator == null) {
            return;
        }
        Span current = tracer.currentSpan();
        if (current == null) {
            return;
        }
        propagator.inject(current.context(), headerSink, (sink, key, value) -> sink.accept(key, value));
    }

    /** Restores a parent span from message headers, or {@code null} when none is present. */
    public Span extract(Map<String, ?> headers) {
        if (tracer == null || propagator == null) {
            return null;
        }
        Span.Builder builder = propagator.extract(headers, (carrier, key) -> {
            Object value = carrier.get(key);
            return value == null ? null : value instanceof byte[] bytes ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8) : value.toString();
        });
        return builder == null ? null : builder.name("cqrs.message").start();
    }

    public boolean isEnabled() {
        return tracer != null;
    }
}
