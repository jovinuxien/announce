package org.linlinjava.announce.payment.infrastructure.projection;

import org.linlinjava.announce.cqrs.command.Subjects;
import org.linlinjava.announce.cqrs.readside.EventEnvelope;
import org.linlinjava.announce.cqrs.readside.ProjectionHandlers;
import org.linlinjava.announce.cqrs.readside.ReactiveProjection;
import org.linlinjava.announce.db.dao.reactive.AnnouncePaymentMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceSellerAccountMapper;
import org.linlinjava.announce.db.domain.AnnouncePayment;
import org.linlinjava.announce.db.domain.AnnounceSellerAccount;
import org.linlinjava.announce.payment.domain.events.PaymentCancelled;
import org.linlinjava.announce.payment.domain.events.PaymentConfirmed;
import org.linlinjava.announce.payment.domain.events.PaymentFailed;
import org.linlinjava.announce.payment.domain.events.PaymentIntentCreated;
import org.linlinjava.announce.payment.domain.events.PayoutRequested;
import org.linlinjava.announce.payment.domain.events.PayoutSent;
import org.linlinjava.announce.payment.domain.events.RefundRequested;
import org.linlinjava.announce.payment.domain.events.RefundSent;
import org.linlinjava.announce.payment.domain.events.SellerAccountCreated;
import org.linlinjava.announce.payment.domain.events.SellerAccountUpdated;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Group {@code payment-read-model}: {@code announce_payment} and {@code announce_seller_account}; upsert by id, at-least-once. */
@Component
public class PaymentReadModelProjection implements ReactiveProjection {

    public static final String GROUP = "payment-read-model";

    private final AnnouncePaymentMapper payments;
    private final AnnounceSellerAccountMapper accounts;
    private final DatabaseClient db;

    public PaymentReadModelProjection(AnnouncePaymentMapper payments, AnnounceSellerAccountMapper accounts, DatabaseClient db) {
        this.payments = payments;
        this.accounts = accounts;
        this.db = db;
    }

    @Override
    public String group() {
        return GROUP;
    }

    @Override
    public void configure(ProjectionHandlers handlers) {
        handlers.on(PaymentIntentCreated.class, (e, env) -> {
            AnnouncePayment row = patch(env, e.at());
            row.setOrderId(idOf(env));
            row.setAnnounceId(e.announceId());
            row.setBuyerId(e.buyerId());
            row.setSellerId(e.sellerId());
            row.setProvider(e.provider());
            row.setIntentRef(e.intentRef());
            row.setClientSecret(e.clientSecret());
            row.setStatus("INTENT_CREATED");
            row.setAmount(money(e.amountCents()));
            row.setFeeAmount(money(e.feeCents()));
            row.setPayoutAmount(money(e.payoutCents()));
            row.setCurrency(e.currency());
            row.setAddTime(e.at());
            return payments.selectByPrimaryKey(row.getId()).hasElement()
                    .flatMap(exists -> exists ? payments.updateByPrimaryKeySelective(row) : payments.insertSelective(row)).then();
        });
        handlers.on(PaymentConfirmed.class, (e, env) -> update(env, "CONFIRMED", e.at(), r -> r.setConfirmedAt(e.at())));
        handlers.on(PaymentFailed.class, (e, env) -> update(env, "FAILED", e.at(), r -> r.setFailureReason(e.reason())));
        handlers.on(PayoutRequested.class, (e, env) -> update(env, "RELEASING", e.at(), r -> { r.setPayoutRef(e.payoutRef()); r.setPayoutAmount(money(e.payoutCents())); }));
        handlers.on(PayoutSent.class, (e, env) -> update(env, "PAID_OUT", e.at(), r -> { r.setPayoutRef(e.payoutRef()); r.setReleasedAt(e.at()); }));
        handlers.on(RefundRequested.class, (e, env) -> update(env, "REFUNDING", e.at(), r -> { r.setRefundRef(e.refundRef()); r.setRefundAmount(money(e.amountCents())); }));
        handlers.on(RefundSent.class, (e, env) -> update(env, "REFUNDED", e.at(), r -> { r.setRefundRef(e.refundRef()); r.setRefundedAt(e.at()); }));
        handlers.on(PaymentCancelled.class, (e, env) -> update(env, "CANCELLED", e.at(), r -> { }));
        handlers.on(SellerAccountCreated.class, (e, env) -> {
            AnnounceSellerAccount row = new AnnounceSellerAccount();
            row.setId(Integer.valueOf(idOf(env)));
            row.setUserId(row.getId());
            row.setProvider(e.provider());
            row.setAccountRef(e.accountRef());
            row.setOnboardingUrl(e.onboardingUrl());
            row.setStatus(e.status());
            row.setPayoutsEnabled(e.payoutsEnabled());
            row.setLastEventId(env.eventId());
            row.setAddTime(e.at());
            row.setUpdateTime(e.at());
            return accounts.selectByPrimaryKey(row.getId()).hasElement()
                    .flatMap(exists -> exists ? accounts.updateByPrimaryKeySelective(row) : accounts.insertSelective(row)).then();
        });
        handlers.on(SellerAccountUpdated.class, (e, env) -> {
            AnnounceSellerAccount row = new AnnounceSellerAccount();
            row.setId(Integer.valueOf(idOf(env)));
            row.setStatus(e.status());
            row.setPayoutsEnabled(e.payoutsEnabled());
            row.setLastEventId(env.eventId());
            row.setUpdateTime(e.at());
            return accounts.updateByPrimaryKeySelective(row).then();
        });
    }

    @Override
    public Mono<Void> reset() {
        return Flux.just("announce_payment", "announce_seller_account").concatMap(t -> db.sql("delete from " + t).then()).then();
    }

    private interface Patch { void apply(AnnouncePayment row); }

    private Mono<Void> update(EventEnvelope env, String status, LocalDateTime at, Patch p) {
        AnnouncePayment row = patch(env, at);
        row.setStatus(status);
        p.apply(row);
        return payments.updateByPrimaryKeySelective(row).then();
    }

    private static AnnouncePayment patch(EventEnvelope env, LocalDateTime at) {
        AnnouncePayment row = new AnnouncePayment();
        row.setId(idOf(env));
        row.setLastEventId(env.eventId());
        row.setUpdateTime(at);
        return row;
    }

    private static String idOf(EventEnvelope env) {
        return Subjects.idOf(env.subject());
    }

    private static BigDecimal money(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }
}
