package org.linlinjava.announce.cqrs.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.cqrs.observability.CorrelationContext;
import org.linlinjava.announce.cqrs.observability.CqrsTracing;
import org.linlinjava.announce.cqrs.observability.MetaDataKeys;
import org.linlinjava.announce.cqrs.readside.EventEnvelope;
import org.linlinjava.announce.cqrs.readside.ProjectionHandlers;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicEventRelayAndSubscriberTest {

    record AnnouncePublished(String title, long priceCents, boolean draft) {
    }

    @PublicEvent(type = "announce.published", version = 2)
    record AnnouncePublishedV2(String announceId, String title, long priceCents) {
    }

    record Unrelated() {
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StreamBridge streamBridge = mock(StreamBridge.class);

    private PublicEventRelay relay() {
        return PublicEventRelay.builder("announce-public", streamBridge, objectMapper, CqrsTracing.disabled())
                .binding("announce-out-0").producer("announce-management")
                .map(AnnouncePublished.class, AnnouncePublishedV2.class, (event, envelope) -> event.draft() ? null
                        : new AnnouncePublishedV2(envelope.subject().substring(envelope.subject().lastIndexOf('/') + 1), event.title(), event.priceCents()))
                .build();
    }

    private static EventEnvelope envelope() {
        return new EventEnvelope("/announce/42", "AnnouncePublished", "ev-100", Instant.now(),
                Map.of(MetaDataKeys.CORRELATION_ID, "corr-1", MetaDataKeys.CAUSATION_ID, "cmd-1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void relayPublishesVersionedMessageWithHeadersAndKey() {
        when(streamBridge.send(eq("announce-out-0"), any(Message.class))).thenReturn(true);
        ProjectionHandlers handlers = new ProjectionHandlers();
        relay().configure(handlers);

        StepVerifier.create(handlers.find(AnnouncePublished.class).handle(new AnnouncePublished("Bike", 12000, false), envelope())).verifyComplete();

        ArgumentCaptor<Message<byte[]>> captor = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge).send(eq("announce-out-0"), captor.capture());
        Message<byte[]> message = captor.getValue();
        assertThat(new String(message.getPayload(), StandardCharsets.UTF_8)).isEqualTo("{\"announceId\":\"42\",\"title\":\"Bike\",\"priceCents\":12000}");
        assertThat(message.getHeaders())
                .containsEntry(PublicEventHeaders.EVENT_TYPE, "announce.published")
                .containsEntry(PublicEventHeaders.EVENT_VERSION, "2")
                .containsEntry(PublicEventHeaders.EVENT_ID, "ev-100")
                .containsEntry(PublicEventHeaders.SUBJECT, "/announce/42")
                .containsEntry(PublicEventHeaders.CORRELATION_ID, "corr-1")
                .containsEntry(PublicEventHeaders.CAUSATION_ID, "ev-100")
                .containsEntry(PublicEventHeaders.PRODUCER, "announce-management");
        assertThat((byte[]) message.getHeaders().get(KafkaHeaders.KEY)).isEqualTo("/announce/42".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void relaySkipsNullMappingsAndFailsWhenBridgeRefuses() {
        ProjectionHandlers handlers = new ProjectionHandlers();
        relay().configure(handlers);

        StepVerifier.create(handlers.find(AnnouncePublished.class).handle(new AnnouncePublished("Draft", 1, true), envelope())).verifyComplete();
        verify(streamBridge, never()).send(any(String.class), any(Message.class));

        when(streamBridge.send(any(String.class), any(Message.class))).thenReturn(false);
        StepVerifier.create(handlers.find(AnnouncePublished.class).handle(new AnnouncePublished("Bike", 1, false), envelope()))
                .expectError(IllegalStateException.class).verify();
        assertThat(handlers.find(Unrelated.class)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void subscriberDecodesTheRelayedMessageAndSkipsUnknownTypes() {
        when(streamBridge.send(any(String.class), any(Message.class))).thenReturn(true);
        Message<byte[]> message = relay().toMessage(new AnnouncePublishedV2("42", "Bike", 12000), envelope());
        List<String> seen = new ArrayList<>();
        PublicEventSubscriber subscriber = PublicEventSubscriber.builder("announce", objectMapper, CqrsTracing.disabled(), Duration.ofSeconds(1))
                .on(AnnouncePublishedV2.class, (event, headers) -> Mono.deferContextual(context -> {
                    seen.add(event.announceId() + ":" + event.title() + ":" + headers.eventId().orElse("?") + ":"
                            + headers.subject().orElse("?") + ":" + CorrelationContext.correlationId(context).orElse("-"));
                    return Mono.empty();
                }))
                .build();

        subscriber.accept(message);
        subscriber.accept(org.springframework.messaging.support.MessageBuilder.withPayload("{}".getBytes())
                .setHeader(PublicEventHeaders.EVENT_TYPE, "announce.deleted".getBytes()).build());
        subscriber.accept(org.springframework.messaging.support.MessageBuilder.withPayload("{}".getBytes()).build());

        assertThat(seen).containsExactly("42:Bike:ev-100:/announce/42:corr-1");
    }

    @Test
    void subscriberPropagatesHandlerFailuresSoTheBinderRetries() {
        Message<byte[]> message = relay().toMessage(new AnnouncePublishedV2("42", "Bike", 1), envelope());
        PublicEventSubscriber subscriber = PublicEventSubscriber.builder("announce", objectMapper, CqrsTracing.disabled(), Duration.ofSeconds(1))
                .on(AnnouncePublishedV2.class, event -> Mono.error(new IllegalStateException("index down")))
                .build();

        assertThatThrownBy(() -> subscriber.accept(message)).isInstanceOf(IllegalStateException.class).hasMessage("index down");
    }

    @Test
    void registryRequiresTheAnnotation() {
        assertThatThrownBy(() -> new PublicEventTypeRegistry().register(Unrelated.class)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new PublicEventTypeRegistry().register(AnnouncePublishedV2.class).resolve("announce.published", 2)).contains(AnnouncePublishedV2.class);
        assertThat(new PublicEventTypeRegistry().register(AnnouncePublishedV2.class).resolve("announce.published", 1)).isEmpty();
    }
}
