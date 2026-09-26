package org.linlinjava.announce.messaging.infrastructure.live;

import org.linlinjava.announce.cqrs.messaging.PublicEventSubscribers;
import org.linlinjava.announce.messaging.application.live.BulletinLive;
import org.linlinjava.announce.messaging.application.live.ConversationStatusLive;
import org.linlinjava.announce.messaging.application.live.MessageLive;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

/**
 * Every node consumes the whole live topic {@code messaging.live} (binding {@code liveEvents-in-0}, one consumer
 * group per node, start offset latest) and pushes each record to the sessions it holds. Delivery is at least once:
 * clients de-duplicate on the message id.
 */
@Configuration(proxyBeanMethods = false)
public class LiveEventsSubscriber {

    @Bean
    Consumer<Message<byte[]>> liveEvents(PublicEventSubscribers subscribers, LiveSessions sessions, LiveFrames frames) {
        return subscribers.forTopic("messaging.live")
                .on(MessageLive.class, live -> Mono.fromRunnable(() -> sessions.toConversation(live.conversationId(), frames.message(live))))
                .on(ConversationStatusLive.class, live -> Mono.fromRunnable(() -> sessions.toConversation(live.conversationId(), frames.status(live))))
                .on(BulletinLive.class, live -> Mono.fromRunnable(() -> sessions.toStaff(live.clientType(), frames.bulletin(live))))
                .build();
    }
}
