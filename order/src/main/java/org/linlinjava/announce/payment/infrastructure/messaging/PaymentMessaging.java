package org.linlinjava.announce.payment.infrastructure.messaging;

import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.cqrs.command.Subjects;
import org.linlinjava.announce.cqrs.messaging.PublicEventRelay;
import org.linlinjava.announce.cqrs.messaging.PublicEventRelays;
import org.linlinjava.announce.cqrs.messaging.PublicEventSubscriber;
import org.linlinjava.announce.cqrs.messaging.PublicEventSubscribers;
import org.linlinjava.announce.db.dao.reactive.AnnouncePaymentMapper;
import org.linlinjava.announce.db.domain.AnnouncePayment;
import org.linlinjava.announce.order.api.OrderAgreedV1;
import org.linlinjava.announce.order.api.OrderCancelledV1;
import org.linlinjava.announce.order.api.PaymentConfirmedV1;
import org.linlinjava.announce.order.api.PaymentFailedV1;
import org.linlinjava.announce.order.api.PaymentIntentCreatedV1;
import org.linlinjava.announce.order.api.PayoutReleaseRequestedV1;
import org.linlinjava.announce.order.api.PayoutSentV1;
import org.linlinjava.announce.order.api.RefundRequestedV1;
import org.linlinjava.announce.order.api.RefundSentV1;
import org.linlinjava.announce.order.api.SellerAccountUpdatedV1;
import org.linlinjava.announce.payment.application.PaymentService;
import org.linlinjava.announce.payment.domain.events.PaymentConfirmed;
import org.linlinjava.announce.payment.domain.events.PaymentFailed;
import org.linlinjava.announce.payment.domain.events.PaymentIntentCreated;
import org.linlinjava.announce.payment.domain.events.PayoutSent;
import org.linlinjava.announce.payment.domain.events.RefundSent;
import org.linlinjava.announce.payment.domain.events.SellerAccountCreated;
import org.linlinjava.announce.payment.domain.events.SellerAccountUpdated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.function.Consumer;

/**
 * The payment context over Kafka. <b>Out:</b> relay {@code payment-public} on {@code payment-out-0} ({@code payment.v1}).
 * <b>In:</b> {@code paymentOrderEvents} on {@code order.v1}: an agreed deal gets an intent, a release request a
 * transfer, a refund request a refund, a cancelled deal a voided intent.
 */
@Configuration(proxyBeanMethods = false)
public class PaymentMessaging {

    public static final String RELAY_GROUP = "payment-public";
    public static final String OUT_BINDING = "payment-out-0";

    private static final Logger log = LoggerFactory.getLogger(PaymentMessaging.class);

    @Bean
    PublicEventRelay paymentPublicEvents(PublicEventRelays relays, AnnouncePaymentMapper payments) {
        return relays.forGroup(RELAY_GROUP).binding(OUT_BINDING)
                .map(PaymentIntentCreated.class, PaymentIntentCreatedV1.class, (e, env) -> new PaymentIntentCreatedV1(id(env), id(env), e.provider(),
                        e.intentRef(), e.clientSecret(), e.amountCents(), e.currency(), e.at()))
                .map(PaymentConfirmed.class, PaymentConfirmedV1.class, (e, env) -> {
                    AnnouncePayment p = row(payments, env);
                    return new PaymentConfirmedV1(id(env), id(env), p.getProvider(), cents(p.getAmount()), p.getCurrency(), e.at());
                })
                .map(PaymentFailed.class, PaymentFailedV1.class, (e, env) -> new PaymentFailedV1(id(env), id(env), row(payments, env).getProvider(), e.reason(), e.at()))
                .map(PayoutSent.class, PayoutSentV1.class, (e, env) -> {
                    AnnouncePayment p = row(payments, env);
                    return new PayoutSentV1(id(env), id(env), p.getSellerId(), cents(p.getPayoutAmount()), p.getCurrency(), e.payoutRef(), e.at());
                })
                .map(RefundSent.class, RefundSentV1.class, (e, env) -> {
                    AnnouncePayment p = row(payments, env);
                    return new RefundSentV1(id(env), id(env), p.getBuyerId(), cents(p.getRefundAmount()), p.getCurrency(), e.refundRef(), "refund", e.at());
                })
                .map(SellerAccountCreated.class, SellerAccountUpdatedV1.class, (e, env) ->
                        new SellerAccountUpdatedV1(Integer.valueOf(id(env)), e.provider(), e.accountRef(), e.status(), e.payoutsEnabled(), e.at()))
                .map(SellerAccountUpdated.class, SellerAccountUpdatedV1.class, (e, env) ->
                        new SellerAccountUpdatedV1(Integer.valueOf(id(env)), null, null, e.status(), e.payoutsEnabled(), e.at()))
                .build();
    }

    @Bean
    Consumer<Message<byte[]>> paymentOrderEvents(PublicEventSubscribers subscribers, PaymentService payments) {
        return subscribers.forTopic("order")
                .on(OrderAgreedV1.class, (e, h) -> tolerant(payments.createIntent(e.orderId(), e.announceId(), e.buyerId(), e.sellerId(), e.priceCents(),
                        e.feeCents(), e.payoutCents(), e.currency(), commandId("intent", h)), e))
                .on(PayoutReleaseRequestedV1.class, (e, h) -> tolerant(payments.release(e.orderId(), e.payoutCents(), commandId("release", h)), e))
                .on(RefundRequestedV1.class, (e, h) -> tolerant(payments.refund(e.orderId(), e.amountCents(), e.reason(), commandId("refund", h)), e))
                .on(OrderCancelledV1.class, (e, h) -> tolerant(payments.cancel(e.orderId(), commandId("cancel", h)), e))
                .build();
    }

    private static Mono<Void> tolerant(Mono<?> work, Object event) {
        return work.then().onErrorResume(CommandRejectedException.class, t -> {
            log.error("order.v1 event {} could not be applied to the payment: {}", event, t.getMessage());
            return Mono.empty();
        });
    }

    private static String commandId(String action, PublicEventSubscriber.Headers headers) {
        return headers.eventId().map(id -> action + ":" + id).orElse(null);
    }

    private static String id(org.linlinjava.announce.cqrs.readside.EventEnvelope env) {
        return Subjects.idOf(env.subject());
    }

    /** Relay mappings run on the processor thread, never the event loop; a missing row makes the framework retry. */
    private static AnnouncePayment row(AnnouncePaymentMapper payments, org.linlinjava.announce.cqrs.readside.EventEnvelope env) {
        AnnouncePayment p = payments.selectByPrimaryKey(id(env)).block();
        if (p == null) throw new IllegalStateException("payment " + id(env) + " not in the read model yet");
        return p;
    }

    private static long cents(BigDecimal amount) {
        return amount == null ? 0L : amount.movePointRight(2).longValueExact();
    }
}
