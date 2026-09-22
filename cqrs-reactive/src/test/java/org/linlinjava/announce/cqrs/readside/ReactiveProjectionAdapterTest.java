package org.linlinjava.announce.cqrs.readside;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.eventhandler.EventHandlerDefinition;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.cqrs.observability.CorrelationContext;
import org.linlinjava.announce.cqrs.observability.CqrsTracing;
import org.linlinjava.announce.cqrs.observability.MetaDataKeys;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReactiveProjectionAdapterTest {

    record Published(String title) {
    }

    record Withdrawn() {
    }

    static class RecordingProjection implements ReactiveProjection {
        final List<String> log = new ArrayList<>();
        final AtomicInteger prepared = new AtomicInteger();
        final AtomicInteger resets = new AtomicInteger();

        @Override
        public String group() {
            return "recording";
        }

        @Override
        public void configure(ProjectionHandlers handlers) {
            handlers.on(Published.class, (event, envelope) -> Mono.deferContextual(context -> {
                log.add(envelope.subject() + ":" + event.title() + ":" + CorrelationContext.correlationId(context).orElse("-"));
                return Mono.empty();
            }));
        }

        @Override
        public Mono<Void> prepare() {
            return Mono.fromRunnable(prepared::incrementAndGet);
        }

        @Override
        public Mono<Void> reset() {
            return Mono.fromRunnable(resets::incrementAndGet);
        }
    }

    static Event raw(String subject, String type) {
        return new Event("announce", subject, type, Map.of(), "1.0", "17", Instant.now(), "application/json", "h", "p");
    }

    private final SimpleTracer tracer = new SimpleTracer();
    private final ReactiveProjectionSupport support = new ReactiveProjectionSupport(new CqrsTracing(tracer, null), Duration.ofSeconds(2));

    @Test
    void definitionMatchesEveryEventAndPreparesOnce() {
        RecordingProjection projection = new RecordingProjection();

        EventHandlerDefinition<Object> first = ReactiveProjectionAdapter.definition(projection, support);
        ReactiveProjectionAdapter.definition(projection, support);

        assertThat(first.group()).isEqualTo("recording");
        assertThat(first.eventClass()).isEqualTo(Object.class);
        assertThat(projection.prepared).hasValue(1);
    }

    @Test
    void dispatchesByEventClassWithEnvelopeAndCorrelationInContext() {
        RecordingProjection projection = new RecordingProjection();
        ReactiveProjectionAdapter adapter = new ReactiveProjectionAdapter(projection, support);

        adapter.handle(new Published("Bike"), Map.of(MetaDataKeys.CORRELATION_ID, "corr-1"), raw("/announce/1", "Published"));
        adapter.handle(new Withdrawn(), Map.of(), raw("/announce/1", "Withdrawn"));

        assertThat(projection.log).containsExactly("/announce/1:Bike:corr-1");
        assertThat(tracer.getSpans()).hasSize(1);
        assertThat(tracer.getSpans().poll().getTags())
                .containsEntry(CqrsTracing.TAG_GROUP, "recording")
                .containsEntry(CqrsTracing.TAG_SUBJECT, "/announce/1")
                .containsEntry(CqrsTracing.TAG_EVENT_TYPE, "Published")
                .containsEntry(CqrsTracing.TAG_CORRELATION_ID, "corr-1");
    }

    @Test
    void handlerErrorAndTimeoutPropagateToTheProcessor() {
        ReactiveProjection failing = new ReactiveProjection() {
            @Override
            public String group() {
                return "failing";
            }

            @Override
            public void configure(ProjectionHandlers handlers) {
                handlers.on(Published.class, event -> Mono.error(new IllegalStateException("db down")));
                handlers.on(Withdrawn.class, event -> Mono.never());
            }
        };
        ReactiveProjectionSupport quick = new ReactiveProjectionSupport(CqrsTracing.disabled(), Duration.ofMillis(100));
        ReactiveProjectionAdapter adapter = new ReactiveProjectionAdapter(failing, quick);

        assertThatThrownBy(() -> adapter.handle(new Published("x"), Map.of(), raw("/announce/1", "Published")))
                .isInstanceOf(IllegalStateException.class).hasMessage("db down");
        assertThatThrownBy(() -> adapter.handle(new Withdrawn(), Map.of(), raw("/announce/1", "Withdrawn")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Timeout");
    }

    @Test
    void handlersResolveSubclasses() {
        ProjectionHandlers handlers = new ProjectionHandlers();
        handlers.on(Object.class, event -> Mono.empty());
        assertThat(handlers.find(Published.class)).isNotNull();
        assertThatThrownBy(() -> handlers.on(Object.class, event -> Mono.empty())).isInstanceOf(IllegalArgumentException.class);
        StepVerifier.create(new ReactiveProjectionAdapter(new RecordingProjection(), support)
                .apply(new Withdrawn(), new EventEnvelope("/a/1", "W", "1", Instant.now(), null))).verifyComplete();
    }
}
