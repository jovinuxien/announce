package org.linlinjava.announce.payment.domain;

import com.opencqrs.framework.command.CommandHandler;
import com.opencqrs.framework.command.CommandHandlerDefinition;
import com.opencqrs.framework.command.StateRebuildingHandler;
import com.opencqrs.framework.command.StateRebuildingHandlerDefinition;
import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.cqrs.command.Subjects;
import org.linlinjava.announce.payment.domain.PaymentCommands.CancelIntent;
import org.linlinjava.announce.payment.domain.PaymentCommands.CreateIntent;
import org.linlinjava.announce.payment.domain.PaymentCommands.RecordAccountEvent;
import org.linlinjava.announce.payment.domain.PaymentCommands.RecordProviderEvent;
import org.linlinjava.announce.payment.domain.PaymentCommands.RequestPayout;
import org.linlinjava.announce.payment.domain.PaymentCommands.RequestRefund;
import org.linlinjava.announce.payment.domain.PaymentCommands.StartOnboarding;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Handlers of the Payment and SellerAccount aggregates. A provider event already applied (same provider event id)
 * is a no-op; one that does not fit the state is rejected with a stable code.
 */
@Configuration(proxyBeanMethods = false)
public class PaymentHandlers {

    public static final StateRebuildingHandlerDefinition<Payment, PaymentIntentCreated> ON_INTENT = new StateRebuildingHandlerDefinition<>(
            Payment.class, PaymentIntentCreated.class,
            (StateRebuildingHandler.FromObjectAndMetaDataAndSubject<Payment, PaymentIntentCreated>) (s, e, m, subject) -> Payment.apply(Subjects.idOf(subject), e));
    public static final StateRebuildingHandlerDefinition<Payment, PaymentConfirmed> ON_CONFIRMED = def(PaymentConfirmed.class, Payment::apply);
    public static final StateRebuildingHandlerDefinition<Payment, PaymentFailed> ON_FAILED = def(PaymentFailed.class, Payment::apply);
    public static final StateRebuildingHandlerDefinition<Payment, PayoutRequested> ON_PAYOUT_REQUESTED = def(PayoutRequested.class, Payment::apply);
    public static final StateRebuildingHandlerDefinition<Payment, PayoutSent> ON_PAYOUT_SENT = def(PayoutSent.class, Payment::apply);
    public static final StateRebuildingHandlerDefinition<Payment, RefundRequested> ON_REFUND_REQUESTED = def(RefundRequested.class, Payment::apply);
    public static final StateRebuildingHandlerDefinition<Payment, RefundSent> ON_REFUND_SENT = def(RefundSent.class, Payment::apply);
    public static final StateRebuildingHandlerDefinition<Payment, PaymentCancelled> ON_CANCELLED = def(PaymentCancelled.class, Payment::apply);
    public static final StateRebuildingHandlerDefinition<SellerAccount, SellerAccountCreated> ON_ACCOUNT_CREATED = new StateRebuildingHandlerDefinition<>(
            SellerAccount.class, SellerAccountCreated.class,
            (StateRebuildingHandler.FromObjectAndMetaDataAndSubject<SellerAccount, SellerAccountCreated>) (s, e, m, subject) ->
                    SellerAccount.apply(Integer.valueOf(Subjects.idOf(subject)), e));
    public static final StateRebuildingHandlerDefinition<SellerAccount, SellerAccountUpdated> ON_ACCOUNT_UPDATED = new StateRebuildingHandlerDefinition<>(
            SellerAccount.class, SellerAccountUpdated.class, (StateRebuildingHandler.FromObject<SellerAccount, SellerAccountUpdated>) SellerAccount::apply);

    private static <E> StateRebuildingHandlerDefinition<Payment, E> def(Class<E> type, StateRebuildingHandler.FromObject<Payment, E> fn) {
        return new StateRebuildingHandlerDefinition<>(Payment.class, type, fn);
    }

    public static CommandHandlerDefinition<Payment, CreateIntent, Payment> createIntent(Clock clock) {
        return new CommandHandlerDefinition<>(Payment.class, CreateIntent.class,
                (CommandHandler.ForCommand<Payment, CreateIntent, Payment>) (c, publisher) -> {
                    if (c.amountCents() < 0 || c.feeCents() < 0 || c.feeCents() > c.amountCents()) {
                        throw new IllegalArgumentException("amount and fee must be non-negative and the fee at most the amount");
                    }
                    PaymentIntentCreated e = new PaymentIntentCreated(c.announceId(), c.buyerId(), c.sellerId(), c.provider(), c.intentRef(),
                            c.clientSecret(), c.amountCents(), c.feeCents(), c.payoutCents(), c.currency(), now(clock));
                    publisher.publish(e);
                    return Payment.apply(c.orderId(), e);
                });
    }

    /** Provider webhooks: idempotent on the provider event id, checked against the state. */
    public static CommandHandlerDefinition<Payment, RecordProviderEvent, Payment> recordProviderEvent(Clock clock) {
        return new CommandHandlerDefinition<>(Payment.class, RecordProviderEvent.class,
                (CommandHandler.ForInstanceAndCommand<Payment, RecordProviderEvent, Payment>) (state, c, publisher) -> {
                    if (state.hasSeen(c.providerEventId())) {
                        return state;
                    }
                    LocalDateTime at = c.at() == null ? now(clock) : c.at();
                    switch (c.kind()) {
                        case CONFIRMED -> {
                            if (state.status() == PaymentStatus.CONFIRMED || state.status() == PaymentStatus.RELEASING || state.status() == PaymentStatus.PAID_OUT) {
                                return state;   // the provider repeats itself with a new event id
                            }
                            if (state.status() != PaymentStatus.INTENT_CREATED && state.status() != PaymentStatus.FAILED) {
                                throw reject("not-payable", "A payment in status " + state.status() + " cannot be confirmed");
                            }
                            PaymentConfirmed e = new PaymentConfirmed(c.providerEventId(), at);
                            publisher.publish(e);
                            return state.apply(e);
                        }
                        case FAILED -> {
                            if (state.status() != PaymentStatus.INTENT_CREATED && state.status() != PaymentStatus.FAILED) {
                                return state;
                            }
                            PaymentFailed e = new PaymentFailed(c.providerEventId(), c.reason(), at);
                            publisher.publish(e);
                            return state.apply(e);
                        }
                        case PAYOUT_SENT -> {
                            if (state.status() == PaymentStatus.PAID_OUT) return state;
                            if (state.status() != PaymentStatus.RELEASING) throw reject("payout-not-requested", "No payout was requested (status " + state.status() + ")");
                            PayoutSent e = new PayoutSent(c.providerEventId(), c.ref() == null ? state.payoutRef() : c.ref(), at);
                            publisher.publish(e);
                            return state.apply(e);
                        }
                        case REFUND_SENT -> {
                            if (state.status() == PaymentStatus.REFUNDED) return state;
                            if (state.status() != PaymentStatus.REFUNDING) throw reject("refund-not-requested", "No refund was requested (status " + state.status() + ")");
                            RefundSent e = new RefundSent(c.providerEventId(), c.ref() == null ? state.refundRef() : c.ref(), at);
                            publisher.publish(e);
                            return state.apply(e);
                        }
                        default -> throw new IllegalArgumentException("Unknown provider event kind " + c.kind());
                    }
                });
    }

    public static CommandHandlerDefinition<Payment, RequestPayout, Payment> requestPayout(Clock clock) {
        return new CommandHandlerDefinition<>(Payment.class, RequestPayout.class,
                (CommandHandler.ForInstanceAndCommand<Payment, RequestPayout, Payment>) (state, c, publisher) -> {
                    if (state.status() == PaymentStatus.RELEASING || state.status() == PaymentStatus.PAID_OUT) return state;
                    if (state.status() != PaymentStatus.CONFIRMED) throw reject("not-confirmed", "Only a confirmed payment can be paid out (status " + state.status() + ")");
                    if (c.payoutCents() > state.amountCents() - state.feeCents()) throw reject("payout-too-high", "The payout exceeds the amount minus the fee");
                    PayoutRequested e = new PayoutRequested(c.payoutCents(), c.payoutRef(), now(clock));
                    publisher.publish(e);
                    return state.apply(e);
                });
    }

    public static CommandHandlerDefinition<Payment, RequestRefund, Payment> requestRefund(Clock clock) {
        return new CommandHandlerDefinition<>(Payment.class, RequestRefund.class,
                (CommandHandler.ForInstanceAndCommand<Payment, RequestRefund, Payment>) (state, c, publisher) -> {
                    if (state.status() == PaymentStatus.REFUNDING || state.status() == PaymentStatus.REFUNDED) return state;
                    if (state.status() != PaymentStatus.CONFIRMED) throw reject("not-confirmed", "Only a confirmed payment can be refunded (status " + state.status() + ")");
                    if (c.amountCents() > state.amountCents()) throw reject("refund-too-high", "The refund exceeds the amount paid");
                    RefundRequested e = new RefundRequested(c.amountCents(), c.refundRef(), c.reason(), now(clock));
                    publisher.publish(e);
                    return state.apply(e);
                });
    }

    public static CommandHandlerDefinition<Payment, CancelIntent, Payment> cancelIntent(Clock clock) {
        return new CommandHandlerDefinition<>(Payment.class, CancelIntent.class,
                (CommandHandler.ForInstanceAndCommand<Payment, CancelIntent, Payment>) (state, c, publisher) -> {
                    if (state.status() != PaymentStatus.INTENT_CREATED && state.status() != PaymentStatus.FAILED) return state;
                    PaymentCancelled e = new PaymentCancelled(now(clock));
                    publisher.publish(e);
                    return state.apply(e);
                });
    }

    public static CommandHandlerDefinition<SellerAccount, StartOnboarding, SellerAccount> startOnboarding(Clock clock) {
        return new CommandHandlerDefinition<>(SellerAccount.class, StartOnboarding.class,
                (CommandHandler.ForCommand<SellerAccount, StartOnboarding, SellerAccount>) (c, publisher) -> {
                    SellerAccountCreated e = new SellerAccountCreated(c.provider(), c.accountRef(), c.onboardingUrl(), c.status(), c.payoutsEnabled(), now(clock));
                    publisher.publish(e);
                    return SellerAccount.apply(c.userId(), e);
                });
    }

    public static CommandHandlerDefinition<SellerAccount, RecordAccountEvent, SellerAccount> recordAccountEvent(Clock clock) {
        return new CommandHandlerDefinition<>(SellerAccount.class, RecordAccountEvent.class,
                (CommandHandler.ForInstanceAndCommand<SellerAccount, RecordAccountEvent, SellerAccount>) (state, c, publisher) -> {
                    if (state.hasSeen(c.providerEventId())) return state;
                    SellerAccountUpdated e = new SellerAccountUpdated(c.providerEventId(), c.status(), c.payoutsEnabled(), c.at() == null ? now(clock) : c.at());
                    publisher.publish(e);
                    return state.apply(e);
                });
    }

    public static List<Object> definitions(Clock clock) {
        return List.of(ON_INTENT, ON_CONFIRMED, ON_FAILED, ON_PAYOUT_REQUESTED, ON_PAYOUT_SENT, ON_REFUND_REQUESTED, ON_REFUND_SENT, ON_CANCELLED,
                createIntent(clock), recordProviderEvent(clock), requestPayout(clock), requestRefund(clock), cancelIntent(clock));
    }

    public static List<Object> sellerDefinitions(Clock clock) {
        return List.of(ON_ACCOUNT_CREATED, ON_ACCOUNT_UPDATED, startOnboarding(clock), recordAccountEvent(clock));
    }

    private static CommandRejectedException reject(String code, String detail) {
        return new CommandRejectedException(HttpStatus.CONFLICT, code, detail);
    }

    private static LocalDateTime now(Clock clock) {
        return LocalDateTime.now(clock);
    }

    @Bean StateRebuildingHandlerDefinition<Payment, PaymentIntentCreated> paymentOnIntent() { return ON_INTENT; }
    @Bean StateRebuildingHandlerDefinition<Payment, PaymentConfirmed> paymentOnConfirmed() { return ON_CONFIRMED; }
    @Bean StateRebuildingHandlerDefinition<Payment, PaymentFailed> paymentOnFailed() { return ON_FAILED; }
    @Bean StateRebuildingHandlerDefinition<Payment, PayoutRequested> paymentOnPayoutRequested() { return ON_PAYOUT_REQUESTED; }
    @Bean StateRebuildingHandlerDefinition<Payment, PayoutSent> paymentOnPayoutSent() { return ON_PAYOUT_SENT; }
    @Bean StateRebuildingHandlerDefinition<Payment, RefundRequested> paymentOnRefundRequested() { return ON_REFUND_REQUESTED; }
    @Bean StateRebuildingHandlerDefinition<Payment, RefundSent> paymentOnRefundSent() { return ON_REFUND_SENT; }
    @Bean StateRebuildingHandlerDefinition<Payment, PaymentCancelled> paymentOnCancelled() { return ON_CANCELLED; }
    @Bean StateRebuildingHandlerDefinition<SellerAccount, SellerAccountCreated> sellerAccountOnCreated() { return ON_ACCOUNT_CREATED; }
    @Bean StateRebuildingHandlerDefinition<SellerAccount, SellerAccountUpdated> sellerAccountOnUpdated() { return ON_ACCOUNT_UPDATED; }
    @Bean CommandHandlerDefinition<Payment, CreateIntent, Payment> paymentCreateIntent(Clock clock) { return createIntent(clock); }
    @Bean CommandHandlerDefinition<Payment, RecordProviderEvent, Payment> paymentRecordProviderEvent(Clock clock) { return recordProviderEvent(clock); }
    @Bean CommandHandlerDefinition<Payment, RequestPayout, Payment> paymentRequestPayout(Clock clock) { return requestPayout(clock); }
    @Bean CommandHandlerDefinition<Payment, RequestRefund, Payment> paymentRequestRefund(Clock clock) { return requestRefund(clock); }
    @Bean CommandHandlerDefinition<Payment, CancelIntent, Payment> paymentCancelIntent(Clock clock) { return cancelIntent(clock); }
    @Bean CommandHandlerDefinition<SellerAccount, StartOnboarding, SellerAccount> sellerStartOnboarding(Clock clock) { return startOnboarding(clock); }
    @Bean CommandHandlerDefinition<SellerAccount, RecordAccountEvent, SellerAccount> sellerRecordAccountEvent(Clock clock) { return recordAccountEvent(clock); }
}
