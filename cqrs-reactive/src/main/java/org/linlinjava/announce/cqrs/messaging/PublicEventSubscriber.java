package org.linlinjava.announce.cqrs.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import org.linlinjava.announce.cqrs.observability.CqrsTracing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * The Lagom {@code subscribe().atLeastOnce()} of this framework: consumes another service's public topic.
 *
 * <p>Build one per topic and expose it as a Spring Cloud Stream function bean:
 * <pre>{@code
 * @Bean
 * Consumer<Message<byte[]>> announceEvents(PublicEventSubscribers subscribers) {
 *     return subscribers.forTopic("announce")
 *             .on(AnnouncePublishedV1.class, (event, headers) -> indexer.index(event))
 *             .build();
 * }
 * }</pre>
 * with {@code spring.cloud.function.definition=announceEvents} and
 * {@code spring.cloud.stream.bindings.announceEvents-in-0.destination=announce.v1} plus a consumer group.
 *
 * <p>Spring Cloud Stream calls the consumer on a binder thread and commits the offset when it returns, so the
 * handler's {@code Mono} is awaited with the configured timeout; an error propagates, the binder retries and
 * eventually dead-letters the record (see {@code spring.cloud.stream.kafka.bindings.*.consumer.enableDlq}).
 * The same record can be delivered twice: handlers de-duplicate on {@link Headers#eventId()}.
 * Unknown event types are skipped and logged at debug level so a producer can add types freely.
 */
public class PublicEventSubscriber implements Consumer<Message<byte[]>> {

    public static final String SPAN_NAME = "cqrs.subscribe";

    private static final Logger log = LoggerFactory.getLogger(PublicEventSubscriber.class);

    /** The public event headers of one record, decoded to strings. */
    public record Headers(Map<String, String> values) {

        public Optional<String> get(String name) {
            return Optional.ofNullable(values.get(name));
        }

        public String eventType() {
            return values.get(PublicEventHeaders.EVENT_TYPE);
        }

        public int eventVersion() {
            String version = values.get(PublicEventHeaders.EVENT_VERSION);
            return version == null ? 1 : Integer.parseInt(version);
        }

        public Optional<String> eventId() {
            return get(PublicEventHeaders.EVENT_ID);
        }

        public Optional<String> subject() {
            return get(PublicEventHeaders.SUBJECT);
        }

        public Optional<String> correlationId() {
            return get(PublicEventHeaders.CORRELATION_ID);
        }

        public Optional<String> causationId() {
            return get(PublicEventHeaders.CAUSATION_ID);
        }

        public static Headers of(MessageHeaders messageHeaders) {
            Map<String, String> decoded = new LinkedHashMap<>();
            messageHeaders.forEach((name, value) -> {
                if (value instanceof byte[] bytes) {
                    decoded.put(name, new String(bytes, StandardCharsets.UTF_8));
                } else if (value != null) {
                    decoded.put(name, value.toString());
                }
            });
            return new Headers(decoded);
        }
    }

    private final String name;
    private final ObjectMapper objectMapper;
    private final CqrsTracing tracing;
    private final Duration timeout;
    private final PublicEventTypeRegistry registry;
    private final Map<Class<?>, BiFunction<Object, Headers, Mono<Void>>> handlers;

    PublicEventSubscriber(Builder builder) {
        this.name = builder.name;
        this.objectMapper = builder.objectMapper;
        this.tracing = builder.tracing;
        this.timeout = builder.timeout;
        this.registry = builder.registry;
        this.handlers = Map.copyOf(builder.handlers);
        if (handlers.isEmpty()) {
            throw new IllegalArgumentException("Subscriber " + name + " handles no events");
        }
    }

    public static Builder builder(String name, ObjectMapper objectMapper, CqrsTracing tracing, Duration timeout) {
        return new Builder(name, objectMapper, tracing, timeout);
    }

    @Override
    public void accept(Message<byte[]> message) {
        handle(message).block(timeout);
    }

    /** The reactive pipeline for one record, without blocking. */
    public Mono<Void> handle(Message<byte[]> message) {
        Headers headers = Headers.of(message.getHeaders());
        String type = headers.eventType();
        if (type == null) {
            log.warn("Subscriber {} received a record without {} header; skipping", name, PublicEventHeaders.EVENT_TYPE);
            return Mono.empty();
        }
        Optional<Class<?>> eventClass = registry.resolve(type, headers.eventVersion());
        if (eventClass.isEmpty()) {
            log.debug("Subscriber {} skips unknown public event {} v{}", name, type, headers.eventVersion());
            return Mono.empty();
        }
        BiFunction<Object, Headers, Mono<Void>> handler = handlers.get(eventClass.get());
        Mono<Void> work = Mono.defer(() -> handler.apply(fromJson(message.getPayload(), eventClass.get()), headers));
        Span parent = tracing.extract(message.getHeaders());
        Mono<Void> traced = tracing.traced(SPAN_NAME, Map.of(
                        CqrsTracing.TAG_GROUP, name,
                        CqrsTracing.TAG_SUBJECT, headers.subject().orElse(""),
                        CqrsTracing.TAG_EVENT_TYPE, type),
                headers.correlationId().orElse(null), parent, work);
        return parent == null ? traced : traced.doFinally(signal -> parent.end());
    }

    private Object fromJson(byte[] payload, Class<?> eventClass) {
        try {
            return objectMapper.readValue(payload, eventClass);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot deserialise public event " + eventClass.getName(), e);
        }
    }

    public static final class Builder {
        private final String name;
        private final ObjectMapper objectMapper;
        private final CqrsTracing tracing;
        private final Duration timeout;
        private final PublicEventTypeRegistry registry = new PublicEventTypeRegistry();
        private final Map<Class<?>, BiFunction<Object, Headers, Mono<Void>>> handlers = new LinkedHashMap<>();

        Builder(String name, ObjectMapper objectMapper, CqrsTracing tracing, Duration timeout) {
            this.name = Objects.requireNonNull(name, "name");
            this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
            this.tracing = tracing == null ? CqrsTracing.disabled() : tracing;
            this.timeout = Objects.requireNonNull(timeout, "timeout");
        }

        @SuppressWarnings("unchecked")
        public <P> Builder on(Class<P> publicEvent, BiFunction<? super P, Headers, Mono<Void>> handler) {
            registry.register(publicEvent);
            handlers.put(publicEvent, (event, headers) -> handler.apply((P) event, headers));
            return this;
        }

        public <P> Builder on(Class<P> publicEvent, java.util.function.Function<? super P, Mono<Void>> handler) {
            return on(publicEvent, (event, headers) -> handler.apply(event));
        }

        public PublicEventSubscriber build() {
            return new PublicEventSubscriber(this);
        }
    }
}
