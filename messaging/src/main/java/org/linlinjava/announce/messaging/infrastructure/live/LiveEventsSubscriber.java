package org.linlinjava.announce.messaging.infrastructure.live;

import io.micrometer.core.instrument.MeterRegistry;
import org.linlinjava.announce.cqrs.messaging.PublicEventSubscriber;
import org.linlinjava.announce.cqrs.messaging.PublicEventSubscribers;
import org.linlinjava.announce.messaging.application.live.BulletinLive;
import org.linlinjava.announce.messaging.application.live.ConversationStatusLive;
import org.linlinjava.announce.messaging.application.live.MessageLive;
import org.linlinjava.announce.messaging.infrastructure.metrics.MessagingMeters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * Every node consumes the whole live topic {@code messaging.live} (binding {@code liveEvents-in-0}, one consumer
 * group per node, start offset latest) and pushes each record to the sessions it holds. Delivery is at least once:
 * clients de-duplicate on the message id.
 *
 * <p>Each record is measured twice: {@code messaging.live.latency} is the record's own timestamp (the event time
 * on the command side) to the push on this node, i.e. the whole path through EventSourcingDB, the relay
 * group, Kafka and this consumer; {@code messaging.live.transport} is the Kafka leg alone, from the relay's send
 * (the Kafka record timestamp, {@code CreateTime} on the producer) to the receipt here, so the store's pick-up
 * and the broker round trip can be told apart. {@code messaging.live.delivered} counts the frames pushed to
 * local sockets.
 */
@Configuration(proxyBeanMethods = false)
public class LiveEventsSubscriber {

    @Bean
    Consumer<Message<byte[]>> liveEvents(PublicEventSubscribers subscribers, LiveSessions sessions, LiveFrames frames,
                                         MeterRegistry meters, Clock clock) {
        return subscribers.forTopic("messaging.live")
                .on(MessageLive.class, (live, headers) -> Mono.fromRunnable(() -> {
                    int pushed = sessions.toConversation(live.conversationId(), frames.message(live));
                    measure(meters, clock, LiveFrames.MESSAGE, live.sentAt(), headers, pushed);
                }))
                .on(ConversationStatusLive.class, (live, headers) -> Mono.fromRunnable(() -> {
                    int pushed = sessions.toConversation(live.conversationId(), frames.status(live));
                    measure(meters, clock, LiveFrames.STATUS, live.at(), headers, pushed);
                }))
                .on(BulletinLive.class, (live, headers) -> Mono.fromRunnable(() -> {
                    int pushed = sessions.toStaff(live.clientType(), frames.bulletin(live));
                    measure(meters, clock, LiveFrames.BULLETIN, live.at(), headers, pushed);
                }))
                .build();
    }

    private static void measure(MeterRegistry meters, Clock clock, String record, LocalDateTime sentAt,
                                PublicEventSubscriber.Headers headers, int pushed) {
        if (sentAt != null) {
            MessagingMeters.liveLatency(meters, record).record(MessagingMeters.nonNegative(Duration.between(sentAt, LocalDateTime.now(clock))));
        }
        // the binder exposes the record timestamp (CreateTime: the producer's clock at send) as a received header
        headers.get(KafkaHeaders.RECEIVED_TIMESTAMP).map(Long::parseLong).ifPresent(sentMillis ->
                MessagingMeters.liveTransport(meters, record).record(MessagingMeters.nonNegative(Duration.ofMillis(clock.millis() - sentMillis))));
        if (pushed > 0) {
            meters.counter(MessagingMeters.LIVE_DELIVERED, MessagingMeters.TAG_RECORD, record).increment(pushed);
        }
    }
}
