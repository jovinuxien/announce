package org.linlinjava.announce.goods.infrastructure.messaging;

import com.opencqrs.framework.command.CommandSubjectDoesNotExistException;
import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.cqrs.command.Subjects;
import org.linlinjava.announce.cqrs.messaging.PublicEventRelay;
import org.linlinjava.announce.cqrs.messaging.PublicEventRelays;
import org.linlinjava.announce.cqrs.messaging.PublicEventSubscriber;
import org.linlinjava.announce.cqrs.messaging.PublicEventSubscribers;
import org.linlinjava.announce.goods.api.AnnounceEditedV1;
import org.linlinjava.announce.goods.api.AnnounceExpiredV1;
import org.linlinjava.announce.goods.api.AnnouncePublishedV1;
import org.linlinjava.announce.goods.api.AnnounceSoldV1;
import org.linlinjava.announce.goods.api.AnnounceWithdrawnV1;
import org.linlinjava.announce.goods.application.announce.AnnouncePublishing;
import org.linlinjava.announce.goods.domain.announce.AnnounceKind;
import org.linlinjava.announce.goods.domain.announce.GoodsDetails;
import org.linlinjava.announce.goods.domain.announce.SellerId;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceEdited;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceExpired;
import org.linlinjava.announce.goods.domain.announce.events.AnnouncePublished;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceSold;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceWithdrawn;
import org.linlinjava.announce.order.api.OrderCancelledV1;
import org.linlinjava.announce.order.api.OrderPaidV1;
import org.linlinjava.announce.order.api.OrderRefundedV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import reactor.core.publisher.Mono;

import java.math.RoundingMode;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The public face of announce-management over Kafka.
 *
 * <p><b>Out:</b> the relay group {@code announce-public} maps every lifecycle event to its {@code *V1} record on
 * binding {@code announce-out-0} (topic {@code announce.v1}). Prices travel in minor units with the site currency
 * ({@code announce.currency}); a job or an event has no price and no sale type.
 *
 * <p><b>In:</b> the function {@code orderEvents} consumes {@code order.v1}: a paid deal marks the announce SOLD on
 * behalf of the seller; a cancelled or refunded deal relists it (a no-op unless it was SOLD). The record's event id
 * is used as command id, so a redelivered record replays the stored result instead of running twice. A paid deal on
 * an announce that is not PUBLISHED any more (already sold by hand, withdrawn) is logged and skipped, never retried.
 */
@Configuration(proxyBeanMethods = false)
public class AnnounceMessaging {

    public static final String RELAY_GROUP = "announce-public";
    public static final String OUT_BINDING = "announce-out-0";

    private static final Logger log = LoggerFactory.getLogger(AnnounceMessaging.class);
    private static final Set<String> IGNORED_ON_PAID = Set.of("not-published", "sold-not-applicable", "not-owner");

    @Bean
    PublicEventRelay announcePublicEvents(PublicEventRelays relays, @Value("${announce.currency:DKK}") String currency) {
        return relays.forGroup(RELAY_GROUP).binding(OUT_BINDING)
                .map(AnnouncePublished.class, AnnouncePublishedV1.class, (event, envelope) -> new AnnouncePublishedV1(
                        Subjects.idOf(envelope.subject()), event.details().kind().name(), event.sellerId().value(),
                        event.content().title(), event.content().mainPicture(), priceCents(event.details()), currency,
                        saleType(event.details()), event.content().categoryId().value(), event.at(), event.expiresAt()))
                .map(AnnounceEdited.class, AnnounceEditedV1.class, (event, envelope) -> new AnnounceEditedV1(
                        Subjects.idOf(envelope.subject()), event.details().kind().name(),
                        event.content().title(), event.content().mainPicture(), priceCents(event.details()), currency,
                        saleType(event.details()), event.content().categoryId().value()))
                .map(AnnounceWithdrawn.class, AnnounceWithdrawnV1.class, (event, envelope) -> new AnnounceWithdrawnV1(Subjects.idOf(envelope.subject()), event.at()))
                .map(AnnounceExpired.class, AnnounceExpiredV1.class, (event, envelope) -> new AnnounceExpiredV1(Subjects.idOf(envelope.subject()), event.at()))
                .map(AnnounceSold.class, AnnounceSoldV1.class, (event, envelope) -> new AnnounceSoldV1(Subjects.idOf(envelope.subject()), event.at()))
                .build();
    }

    @Bean
    Consumer<Message<byte[]>> orderEvents(PublicEventSubscribers subscribers, AnnouncePublishing publishing) {
        return subscribers.forTopic("order")
                .on(OrderPaidV1.class, (event, headers) -> publishing
                        .markSold(event.announceId(), new SellerId(event.sellerId()), commandId("mark-sold", headers))
                        .then()
                        .onErrorResume(AnnounceMessaging::ignoredOnPaid, e -> skip(event, e)))
                .on(OrderCancelledV1.class, (event, headers) -> publishing
                        .relist(event.announceId(), commandId("relist", headers)).then()
                        .onErrorResume(CommandSubjectDoesNotExistException.class, e -> skip(event, e)))
                .on(OrderRefundedV1.class, (event, headers) -> publishing
                        .relist(event.announceId(), commandId("relist", headers)).then()
                        .onErrorResume(CommandSubjectDoesNotExistException.class, e -> skip(event, e)))
                .build();
    }

    private static String commandId(String action, PublicEventSubscriber.Headers headers) {
        return headers.eventId().map(id -> action + ":" + id).orElse(null);
    }

    private static boolean ignoredOnPaid(Throwable e) {
        return e instanceof CommandSubjectDoesNotExistException
                || (e instanceof CommandRejectedException rejected && IGNORED_ON_PAID.contains(rejected.getCode()));
    }

    private static Mono<Void> skip(Object event, Throwable cause) {
        log.warn("order.v1 event {} not applied to the announce: {}", event, cause.getMessage());
        return Mono.empty();
    }

    static long priceCents(AnnounceKind details) {
        if (details instanceof GoodsDetails goods) {
            return goods.price().amount().movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        }
        return 0L;
    }

    static String saleType(AnnounceKind details) {
        return details instanceof GoodsDetails goods ? goods.saleType().name() : null;
    }
}
