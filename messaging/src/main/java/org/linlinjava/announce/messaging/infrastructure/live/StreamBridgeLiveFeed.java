package org.linlinjava.announce.messaging.infrastructure.live;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.announce.cqrs.messaging.PublicEventHeaders;
import org.linlinjava.announce.cqrs.messaging.PublicEventTypeRegistry;
import org.linlinjava.announce.messaging.application.live.LiveFeed;
import org.linlinjava.announce.messaging.infrastructure.messaging.MessagingRelays;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes records that do not come from the event store (bulletins) on the live binding with the same headers
 * the relay sets, so the live subscriber decodes them alike.
 */
@Component
public class StreamBridgeLiveFeed implements LiveFeed {

    private final StreamBridge streamBridge;
    private final ObjectMapper objectMapper;
    private final String producer;

    public StreamBridgeLiveFeed(StreamBridge streamBridge, ObjectMapper objectMapper,
                                @org.springframework.beans.factory.annotation.Value("${spring.application.name:messaging}") String producer) {
        this.streamBridge = streamBridge;
        this.objectMapper = objectMapper;
        this.producer = producer;
    }

    @Override
    public Mono<Void> publish(String key, Object liveRecord) {
        return Mono.fromRunnable(() -> {
            PublicEventTypeRegistry.Key type = PublicEventTypeRegistry.keyOf(liveRecord.getClass());
            Map<String, Object> headers = new LinkedHashMap<>();
            headers.put(PublicEventHeaders.EVENT_TYPE, type.type());
            headers.put(PublicEventHeaders.EVENT_VERSION, String.valueOf(type.version()));
            headers.put(PublicEventHeaders.EVENT_ID, UUID.randomUUID().toString());
            headers.put(PublicEventHeaders.PRODUCER, producer);
            headers.put(KafkaHeaders.KEY, key.getBytes(StandardCharsets.UTF_8));
            headers.put("contentType", MimeTypeUtils.APPLICATION_JSON_VALUE);
            Message<byte[]> message = MessageBuilder.withPayload(toJson(liveRecord)).copyHeaders(headers).build();
            if (!streamBridge.send(MessagingRelays.LIVE_BINDING, message)) {
                throw new IllegalStateException("StreamBridge refused live record for " + MessagingRelays.LIVE_BINDING);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private byte[] toJson(Object record) {
        try {
            return objectMapper.writeValueAsBytes(record);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise live record " + record.getClass().getName(), e);
        }
    }
}
