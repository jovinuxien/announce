package org.linlinjava.announce.cqrs.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import reactor.util.context.ContextView;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fills {@code correlationId} and {@code causationId}:
 * <ul>
 *   <li>correlation id: the caller's value, else the Reactor context, else the current trace id, else a new UUID;</li>
 *   <li>causation id: the caller's value, else the command id when one is given.</li>
 * </ul>
 */
public class TracingMetaDataEnricher implements MetaDataEnricher {

    private final Tracer tracer;

    /**
     * @param tracer the Micrometer tracer, or {@code null} when tracing is not configured
     */
    public TracingMetaDataEnricher(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Map<String, Object> enrich(Map<String, ?> given, ContextView context) {
        Map<String, Object> result = new LinkedHashMap<>(given);
        result.computeIfAbsent(MetaDataKeys.CORRELATION_ID, key -> CorrelationContext.correlationId(context)
                .or(this::currentTraceId)
                .orElseGet(CorrelationContext::newCorrelationId));
        Object commandId = result.get(MetaDataKeys.COMMAND_ID);
        if (commandId != null) {
            result.putIfAbsent(MetaDataKeys.CAUSATION_ID, commandId);
        }
        return result;
    }

    private java.util.Optional<String> currentTraceId() {
        if (tracer == null) {
            return java.util.Optional.empty();
        }
        Span span = tracer.currentSpan();
        return span == null ? java.util.Optional.empty() : java.util.Optional.ofNullable(span.context().traceId());
    }
}
