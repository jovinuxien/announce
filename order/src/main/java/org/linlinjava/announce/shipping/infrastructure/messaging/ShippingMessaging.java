package org.linlinjava.announce.shipping.infrastructure.messaging;

import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.cqrs.command.Subjects;
import org.linlinjava.announce.cqrs.messaging.PublicEventRelay;
import org.linlinjava.announce.cqrs.messaging.PublicEventRelays;
import org.linlinjava.announce.cqrs.messaging.PublicEventSubscriber;
import org.linlinjava.announce.cqrs.messaging.PublicEventSubscribers;
import org.linlinjava.announce.order.api.OrderCancelledV1;
import org.linlinjava.announce.order.api.OrderPaidV1;
import org.linlinjava.announce.order.api.OrderRefundedV1;
import org.linlinjava.announce.order.api.ShipmentDeliveredV1;
import org.linlinjava.announce.order.api.ShipmentDispatchedV1;
import org.linlinjava.announce.order.api.ShipmentFailedV1;
import org.linlinjava.announce.order.api.ShipmentInTransitV1;
import org.linlinjava.announce.order.api.ShipmentRequestedV1;
import org.linlinjava.announce.shipping.application.ShippingService;
import org.linlinjava.announce.shipping.domain.events.ShipmentDelivered;
import org.linlinjava.announce.shipping.domain.events.ShipmentDispatched;
import org.linlinjava.announce.shipping.domain.events.ShipmentFailed;
import org.linlinjava.announce.shipping.domain.events.ShipmentInTransit;
import org.linlinjava.announce.shipping.domain.events.ShipmentRequested;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

/**
 * The shipping context over Kafka. <b>Out:</b> relay {@code shipping-public} on {@code shipping-out-0}
 * ({@code shipping.v1}); every event carries what the record needs, no read-model lookup. <b>In:</b>
 * {@code shippingOrderEvents} on {@code order.v1}: a paid deal opens its shipment; a cancelled or refunded deal
 * cancels one that was not dispatched.
 */
@Configuration(proxyBeanMethods = false)
public class ShippingMessaging {

    public static final String RELAY_GROUP = "shipping-public";
    public static final String OUT_BINDING = "shipping-out-0";

    private static final Logger log = LoggerFactory.getLogger(ShippingMessaging.class);

    @Bean
    PublicEventRelay shippingPublicEvents(PublicEventRelays relays) {
        return relays.forGroup(RELAY_GROUP).binding(OUT_BINDING)
                .map(ShipmentRequested.class, ShipmentRequestedV1.class, (e, env) -> new ShipmentRequestedV1(id(env), e.sellerId(), e.buyerId(), e.carrier(),
                        e.service(), e.trackingNumber(), e.labelUrl(), e.at()))
                .map(ShipmentDispatched.class, ShipmentDispatchedV1.class, (e, env) -> new ShipmentDispatchedV1(id(env), e.carrier(), e.trackingNumber(), e.at()))
                .map(ShipmentInTransit.class, ShipmentInTransitV1.class, (e, env) -> new ShipmentInTransitV1(id(env), e.carrier(), e.trackingNumber(), e.at()))
                .map(ShipmentDelivered.class, ShipmentDeliveredV1.class, (e, env) -> new ShipmentDeliveredV1(id(env), e.carrier(), e.trackingNumber(), e.at()))
                .map(ShipmentFailed.class, ShipmentFailedV1.class, (e, env) -> new ShipmentFailedV1(id(env), e.carrier(), e.trackingNumber(), e.reason(), e.at()))
                .build();
    }

    @Bean
    Consumer<Message<byte[]>> shippingOrderEvents(PublicEventSubscribers subscribers, ShippingService shipping) {
        return subscribers.forTopic("order")
                .on(OrderPaidV1.class, (e, h) -> tolerant(shipping.open(e.orderId(), e.sellerId(), e.buyerId(), commandId("open", h)), e))
                .on(OrderCancelledV1.class, (e, h) -> tolerant(shipping.cancel(e.orderId(), "order cancelled", commandId("cancel", h)), e))
                .on(OrderRefundedV1.class, (e, h) -> tolerant(shipping.cancel(e.orderId(), "order refunded", commandId("cancel", h)), e))
                .build();
    }

    private static Mono<Void> tolerant(Mono<?> work, Object event) {
        return work.then().onErrorResume(CommandRejectedException.class, t -> {
            log.error("order.v1 event {} could not be applied to the shipment: {}", event, t.getMessage());
            return Mono.empty();
        });
    }

    private static String commandId(String action, PublicEventSubscriber.Headers headers) {
        return headers.eventId().map(id -> action + ":" + id).orElse(null);
    }

    private static String id(org.linlinjava.announce.cqrs.readside.EventEnvelope env) {
        return Subjects.idOf(env.subject());
    }
}
