package org.linlinjava.announce.messaging.infrastructure.messaging.order;

import org.linlinjava.announce.cqrs.messaging.PublicEventSubscriber.Headers;
import org.linlinjava.announce.cqrs.messaging.PublicEventSubscribers;
import org.linlinjava.announce.messaging.application.Conversations;
import org.linlinjava.announce.messaging.domain.conversation.Conversation;
import org.linlinjava.announce.order.api.OrderAgreedV1;
import org.linlinjava.announce.order.api.OrderPaidV1;
import org.linlinjava.announce.order.api.OrderSettledV1;
import org.linlinjava.announce.order.api.OrderShippedV1;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.function.Consumer;

/**
 * Subscriber of {@code order.v1} (binding {@code orderEvents-in-0}; records of {@code order-api}): every step of a deal becomes a SYSTEM message
 * in the listing conversation of the buyer and seller. The conversation is opened when it does not exist yet (a
 * deal from an auction or a direct purchase may have had no chat), which is idempotent because its id derives
 * from the announce and the buyer. The message's client id is the Kafka event id, so a redelivered record posts
 * nothing twice.
 */
@Configuration(proxyBeanMethods = false)
public class OrderEventsSubscriber {

    @Bean
    Consumer<Message<byte[]>> orderEvents(PublicEventSubscribers subscribers, Conversations conversations) {
        return subscribers.forTopic("order")
                .on(OrderAgreedV1.class, (event, headers) -> post(conversations, event.announceId(), event.buyerId(), event.sellerId(),
                        "Deal agreed at " + money(event.priceCents(), event.currency()) + ". The buyer can now pay.", key(headers, "agreed", event.orderId())))
                .on(OrderPaidV1.class, (event, headers) -> post(conversations, event.announceId(), event.buyerId(), event.sellerId(),
                        "Payment of " + money(event.amountCents(), event.currency()) + " received. The seller can now ship.", key(headers, "paid", event.orderId())))
                .on(OrderShippedV1.class, (event, headers) -> post(conversations, event.announceId(), event.buyerId(), event.sellerId(),
                        "Shipped" + (event.trackingNumber() == null ? "" : ", tracking " + event.trackingNumber()) + ".", key(headers, "shipped", event.orderId())))
                .on(OrderSettledV1.class, (event, headers) -> post(conversations, event.announceId(), event.buyerId(), event.sellerId(),
                        "Deal completed: the payout was released to the seller.", key(headers, "settled", event.orderId())))
                .build();
    }

    private static Mono<Void> post(Conversations conversations, String announceId, Integer buyerId, Integer sellerId, String text, String clientMsgId) {
        return conversations.openListing(announceId, buyerId, sellerId, null)
                .map(Conversation::id)
                .flatMap(id -> conversations.postSystem(id, text, clientMsgId))
                .then();
    }

    private static String key(Headers headers, String step, String orderId) {
        return "order:" + headers.eventId().orElse(step + ":" + orderId);
    }

    private static String money(long cents, String currency) {
        return BigDecimal.valueOf(cents, 2).toPlainString() + " " + (currency == null ? "" : currency);
    }
}
