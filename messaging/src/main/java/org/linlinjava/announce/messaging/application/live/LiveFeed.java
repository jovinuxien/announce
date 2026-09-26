package org.linlinjava.announce.messaging.application.live;

import reactor.core.publisher.Mono;

/**
 * Port to the live fan-out topic ({@code messaging.live}) for records that do not come from the event store
 * (bulletins). Conversation events reach the topic through the relay group {@code messaging-live}.
 */
public interface LiveFeed {

    /** Publishes one live record; {@code key} decides the partition. */
    Mono<Void> publish(String key, Object liveRecord);
}
