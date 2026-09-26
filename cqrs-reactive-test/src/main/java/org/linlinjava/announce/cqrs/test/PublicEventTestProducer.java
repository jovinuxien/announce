package org.linlinjava.announce.cqrs.test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.announce.cqrs.messaging.PublicEvent;
import org.linlinjava.announce.cqrs.messaging.PublicEventHeaders;
import org.linlinjava.announce.cqrs.messaging.PublicEventTypeRegistry;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Stands in for another service's {@code PublicEventRelay}: sends a {@link PublicEvent} record to a topic with the
 * same headers a relay writes ({@code event-type}, {@code event-version}, {@code event-id}, {@code subject},
 * {@code causation-id}, {@code producer}) and the subject as Kafka key, so the service under test consumes it
 * through its ordinary {@code PublicEventSubscriber}. Pass the topic name as binding: {@link StreamBridge} creates
 * an output binding of that name on the fly, and a binding configured under
 * {@code spring.cloud.stream.bindings.<name>.destination} is honoured as well.
 *
 * <pre>{@code
 * PublicEventTestProducer producer = new PublicEventTestProducer(streamBridge, objectMapper, "order-test");
 * producer.send("order.v1", "/order/42", new OrderPaidV1(...));
 * }</pre>
 */
public final class PublicEventTestProducer {

    private final StreamBridge streamBridge;
    private final ObjectMapper objectMapper;
    private final String producer;
    private final PublicEventTypeRegistry registry = new PublicEventTypeRegistry();

    public PublicEventTestProducer(StreamBridge streamBridge, ObjectMapper objectMapper, String producer) {
        this.streamBridge = Objects.requireNonNull(streamBridge, "streamBridge");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.producer = producer;
    }

    /** Sends the event with a fresh event id and returns that id. */
    public String send(String binding, String subject, Object publicEvent) {
        String eventId = UUID.randomUUID().toString();
        send(binding, subject, publicEvent, eventId);
        return eventId;
    }

    /** Sends the event under the given event id (send twice with the same id to test consumer de-duplication). */
    public void send(String binding, String subject, Object publicEvent, String eventId) {
        registry.register(publicEvent.getClass());
        PublicEventTypeRegistry.Key key = registry.keyFor(publicEvent.getClass());
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put(PublicEventHeaders.EVENT_TYPE, key.type());
        headers.put(PublicEventHeaders.EVENT_VERSION, String.valueOf(key.version()));
        headers.put(PublicEventHeaders.EVENT_ID, eventId);
        headers.put(PublicEventHeaders.SUBJECT, subject);
        headers.put(PublicEventHeaders.CAUSATION_ID, eventId);
        headers.put(PublicEventHeaders.CORRELATION_ID, "test-" + eventId);
        if (producer != null) {
            headers.put(PublicEventHeaders.PRODUCER, producer);
        }
        headers.put(KafkaHeaders.KEY, subject.getBytes(StandardCharsets.UTF_8));
        headers.put("contentType", MimeTypeUtils.APPLICATION_JSON_VALUE);
        Message<byte[]> message = MessageBuilder.withPayload(toJson(publicEvent)).copyHeaders(headers).build();
        if (!streamBridge.send(binding, message)) {
            throw new IllegalStateException("StreamBridge refused the message for binding " + binding);
        }
    }

    private byte[] toJson(Object publicEvent) {
        try {
            return objectMapper.writeValueAsBytes(publicEvent);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise public event " + publicEvent.getClass().getName(), e);
        }
    }
}
