package org.linlinjava.announce.cqrs.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.announce.cqrs.observability.CqrsTracing;
import org.springframework.cloud.stream.function.StreamBridge;

/**
 * Auto-configured factory of {@link PublicEventRelay} builders.
 */
public class PublicEventRelays {

    private final StreamBridge streamBridge;
    private final ObjectMapper objectMapper;
    private final CqrsTracing tracing;
    private final String producer;

    public PublicEventRelays(StreamBridge streamBridge, ObjectMapper objectMapper, CqrsTracing tracing, String producer) {
        this.streamBridge = streamBridge;
        this.objectMapper = objectMapper;
        this.tracing = tracing;
        this.producer = producer;
    }

    public PublicEventRelay.Builder forGroup(String group) {
        return PublicEventRelay.builder(group, streamBridge, objectMapper, tracing).producer(producer);
    }
}
