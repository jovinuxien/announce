package org.linlinjava.announce.cqrs.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.announce.cqrs.observability.CqrsTracing;
import org.linlinjava.announce.cqrs.readside.EventEnvelope;
import org.linlinjava.announce.cqrs.readside.ProjectionHandlers;
import org.linlinjava.announce.cqrs.readside.ReactiveProjection;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * The Lagom {@code TopicProducer.taggedStreamWithOffset} of this framework: an event handling group that
 * maps internal events to versioned {@link PublicEvent public events} and publishes them through Spring
 * Cloud Stream's {@link StreamBridge}.
 *
 * <p>Because it is a {@link ReactiveProjection}, its checkpoint is tracked like any other group: a message is
 * sent, then the checkpoint advances. A crash in between re-sends the message, so delivery is at-least-once
 * and consumers de-duplicate on the {@code event-id} header. The Kafka record key is the subject, which keeps
 * one aggregate's messages in order on one partition.
 *
 * <pre>{@code
 * @Bean
 * PublicEventRelay announcePublicEvents(PublicEventRelays relays) {
 *     return relays.forGroup("announce-public").binding("announce-out-0")
 *             .map(AnnouncePublished.class, (e, env) -> new AnnouncePublishedV1(Subjects.idOf(env.subject()), e.title()))
 *             .build();
 * }
 * }</pre>
 * with {@code spring.cloud.stream.bindings.announce-out-0.destination=announce.v1}.
 */
public class PublicEventRelay implements ReactiveProjection {

    public static final String SPAN_NAME = "cqrs.publish";

    private final String group;
    private final String binding;
    private final String producer;
    private final StreamBridge streamBridge;
    private final ObjectMapper objectMapper;
    private final CqrsTracing tracing;
    private final PublicEventTypeRegistry registry;
    private final Map<Class<?>, BiFunction<Object, EventEnvelope, Object>> mappers;

    PublicEventRelay(Builder builder) {
        this.group = builder.group;
        this.binding = Objects.requireNonNull(builder.binding, "binding");
        this.producer = builder.producer;
        this.streamBridge = builder.streamBridge;
        this.objectMapper = builder.objectMapper;
        this.tracing = builder.tracing;
        this.registry = builder.registry;
        this.mappers = Map.copyOf(builder.mappers);
        if (mappers.isEmpty()) {
            throw new IllegalArgumentException("Relay " + group + " maps no events");
        }
    }

    public static Builder builder(String group, StreamBridge streamBridge, ObjectMapper objectMapper, CqrsTracing tracing) {
        return new Builder(group, streamBridge, objectMapper, tracing);
    }

    @Override
    public String group() {
        return group;
    }

    public String binding() {
        return binding;
    }

    @Override
    public void configure(ProjectionHandlers handlers) {
        mappers.forEach((eventClass, mapper) -> handlers.on(eventClass, (event, envelope) -> publish(event, envelope)));
    }

    Mono<Void> publish(Object event, EventEnvelope envelope) {
        return Mono.fromRunnable(() -> {
            Object publicEvent = mappers.get(event.getClass()).apply(event, envelope);
            if (publicEvent == null) {
                return;
            }
            Message<byte[]> message = toMessage(publicEvent, envelope);
            if (!streamBridge.send(binding, message)) {
                throw new IllegalStateException("StreamBridge refused message for binding " + binding);
            }
        }).then().transform(work -> tracing.traced(SPAN_NAME, Map.of(
                        CqrsTracing.TAG_GROUP, group,
                        CqrsTracing.TAG_SUBJECT, envelope.subject(),
                        CqrsTracing.TAG_EVENT_TYPE, envelope.type()),
                envelope.correlationId().orElse(null), null, work));
    }

    Message<byte[]> toMessage(Object publicEvent, EventEnvelope envelope) {
        PublicEventTypeRegistry.Key key = registry.keyFor(publicEvent.getClass());
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put(PublicEventHeaders.EVENT_TYPE, key.type());
        headers.put(PublicEventHeaders.EVENT_VERSION, String.valueOf(key.version()));
        headers.put(PublicEventHeaders.EVENT_ID, envelope.eventId());
        headers.put(PublicEventHeaders.SUBJECT, envelope.subject());
        headers.put(PublicEventHeaders.CAUSATION_ID, envelope.eventId());
        envelope.correlationId().ifPresent(id -> headers.put(PublicEventHeaders.CORRELATION_ID, id));
        if (producer != null) {
            headers.put(PublicEventHeaders.PRODUCER, producer);
        }
        tracing.inject(headers::put);
        headers.put(KafkaHeaders.KEY, envelope.subject().getBytes(StandardCharsets.UTF_8));
        headers.put("contentType", MimeTypeUtils.APPLICATION_JSON_VALUE);
        return MessageBuilder.withPayload(toJson(publicEvent)).copyHeaders(headers).build();
    }

    private byte[] toJson(Object publicEvent) {
        try {
            return objectMapper.writeValueAsBytes(publicEvent);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise public event " + publicEvent.getClass().getName(), e);
        }
    }

    public static final class Builder {
        private final String group;
        private final StreamBridge streamBridge;
        private final ObjectMapper objectMapper;
        private final CqrsTracing tracing;
        private final PublicEventTypeRegistry registry = new PublicEventTypeRegistry();
        private final Map<Class<?>, BiFunction<Object, EventEnvelope, Object>> mappers = new LinkedHashMap<>();
        private String binding;
        private String producer;

        Builder(String group, StreamBridge streamBridge, ObjectMapper objectMapper, CqrsTracing tracing) {
            this.group = Objects.requireNonNull(group, "group");
            this.streamBridge = Objects.requireNonNull(streamBridge, "streamBridge");
            this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
            this.tracing = tracing == null ? CqrsTracing.disabled() : tracing;
        }

        /** The Spring Cloud Stream output binding name, for example {@code announce-out-0}. */
        public Builder binding(String binding) {
            this.binding = binding;
            return this;
        }

        /** Value of the {@code producer} header, typically the service name. */
        public Builder producer(String producer) {
            this.producer = producer;
            return this;
        }

        /** Maps an internal event to a public event; returning {@code null} skips the event. */
        @SuppressWarnings("unchecked")
        public <E, P> Builder map(Class<E> internalEvent, Class<P> publicEvent, BiFunction<? super E, EventEnvelope, ? extends P> mapper) {
            registry.register(publicEvent);
            mappers.put(internalEvent, (event, envelope) -> mapper.apply((E) event, envelope));
            return this;
        }

        public <E, P> Builder map(Class<E> internalEvent, Class<P> publicEvent, java.util.function.Function<? super E, ? extends P> mapper) {
            return map(internalEvent, publicEvent, (event, envelope) -> mapper.apply(event));
        }

        public PublicEventRelay build() {
            return new PublicEventRelay(this);
        }
    }
}
