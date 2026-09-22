package org.linlinjava.announce.cqrs.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.Test;
import reactor.util.context.Context;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TracingMetaDataEnricherTest {

    private final SimpleTracer tracer = new SimpleTracer();
    private final MetaDataEnricher enricher = new TracingMetaDataEnricher(tracer);

    @Test
    void callerValuesWin() {
        Map<String, Object> result = enricher.enrich(Map.of(MetaDataKeys.CORRELATION_ID, "given",
                MetaDataKeys.COMMAND_ID, "c1", MetaDataKeys.CAUSATION_ID, "cause"), Context.of(CorrelationContext.KEY, "ctx"));

        assertThat(result).containsEntry(MetaDataKeys.CORRELATION_ID, "given").containsEntry(MetaDataKeys.CAUSATION_ID, "cause");
    }

    @Test
    void reactorContextBeatsCurrentTrace() {
        Span span = tracer.nextSpan().start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            Map<String, Object> result = enricher.enrich(Map.of(), Context.of(CorrelationContext.KEY, "ctx"));
            assertThat(result).containsEntry(MetaDataKeys.CORRELATION_ID, "ctx");
        } finally {
            span.end();
        }
    }

    @Test
    void currentTraceIdIsUsedWhenNothingElseIsKnown() {
        Span span = tracer.nextSpan().start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            Map<String, Object> result = enricher.enrich(Map.of(MetaDataKeys.COMMAND_ID, "c1"), Context.empty());
            assertThat(result).containsEntry(MetaDataKeys.CORRELATION_ID, span.context().traceId())
                    .containsEntry(MetaDataKeys.CAUSATION_ID, "c1");
        } finally {
            span.end();
        }
    }

    @Test
    void doesNotMutateTheGivenMap() {
        Map<String, Object> given = new java.util.HashMap<>(Map.of(MetaDataKeys.COMMAND_ID, "c1"));
        enricher.enrich(given, Context.empty());
        assertThat(given).hasSize(1);
    }
}
