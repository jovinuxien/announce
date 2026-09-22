package org.linlinjava.announce.cqrs.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.announce.cqrs.observability.CqrsTracing;

import java.time.Duration;

/**
 * Auto-configured factory of {@link PublicEventSubscriber} builders.
 */
public class PublicEventSubscribers {

    private final ObjectMapper objectMapper;
    private final CqrsTracing tracing;
    private final Duration timeout;

    public PublicEventSubscribers(ObjectMapper objectMapper, CqrsTracing tracing, Duration timeout) {
        this.objectMapper = objectMapper;
        this.tracing = tracing;
        this.timeout = timeout;
    }

    /** @param name a name for logs and spans, typically the topic or the producing service */
    public PublicEventSubscriber.Builder forTopic(String name) {
        return PublicEventSubscriber.builder(name, objectMapper, tracing, timeout);
    }
}
