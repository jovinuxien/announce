package org.linlinjava.announce.payment.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencqrs.framework.command.CommandSubjectAlreadyExistsException;
import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.cqrs.command.ReactiveCommandRouter;
import org.linlinjava.announce.cqrs.observability.MetaDataKeys;
import org.linlinjava.announce.db.dao.reactive.AnnouncePaymentMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceSellerAccountMapper;
import org.linlinjava.announce.db.domain.AnnouncePayment;
import org.linlinjava.announce.db.domain.AnnounceSellerAccount;
import org.linlinjava.announce.payment.domain.Payment;
import org.linlinjava.announce.payment.domain.PaymentCommands.CancelIntent;
import org.linlinjava.announce.payment.domain.PaymentCommands.CreateIntent;
import org.linlinjava.announce.payment.domain.PaymentCommands.RecordAccountEvent;
import org.linlinjava.announce.payment.domain.PaymentCommands.RecordProviderEvent;
import org.linlinjava.announce.payment.domain.PaymentCommands.RequestPayout;
import org.linlinjava.announce.payment.domain.PaymentCommands.RequestRefund;
import org.linlinjava.announce.payment.domain.PaymentCommands.StartOnboarding;
import org.linlinjava.announce.payment.domain.PaymentProvider;
import org.linlinjava.announce.payment.domain.ProviderEventKind;
import org.linlinjava.announce.payment.domain.SellerAccount;
import org.linlinjava.announce.payment.infrastructure.config.PaymentProperties;
import org.linlinjava.announce.payment.infrastructure.provider.WebhookSignature;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Use cases of the payment context: call the provider, then record the outcome as a command; verify and apply
 * webhooks; onboard sellers. Reads come from {@code announce_payment} and {@code announce_seller_account}.
 */
@Service
public class PaymentService {

    private final ReactiveCommandRouter router;
    private final PaymentProvider provider;
    private final AnnouncePaymentMapper payments;
    private final AnnounceSellerAccountMapper accounts;
    private final PaymentProperties properties;
    private final ObjectMapper json;

    public PaymentService(ReactiveCommandRouter router, PaymentProvider provider, AnnouncePaymentMapper payments,
                          AnnounceSellerAccountMapper accounts, PaymentProperties properties, ObjectMapper json) {
        this.router = router;
        this.provider = provider;
        this.payments = payments;
        this.accounts = accounts;
        this.properties = properties;
        this.json = json;
    }

    /** From OrderAgreedV1: holds the amount at the provider. Idempotent: an existing payment for the order is left alone. */
    public Mono<Payment> createIntent(String orderId, String announceId, Integer buyerId, Integer sellerId, long amountCents, long feeCents,
                                      long payoutCents, String currency, String commandId) {
        return payments.selectByPrimaryKey(orderId).hasElement().flatMap(exists -> {
            if (exists) {
                return Mono.empty();
            }
            return provider.createIntent(new PaymentProvider.IntentRequest(orderId, buyerId, sellerId, amountCents, feeCents, currency))
                    .flatMap(intent -> send(new CreateIntent(orderId, announceId, buyerId, sellerId, amountCents, feeCents, payoutCents, currency,
                            provider.name(), intent.intentRef(), intent.clientSecret()), commandId, Payment.class))
                    .onErrorResume(CommandSubjectAlreadyExistsException.class, e -> Mono.empty());
        });
    }

    /** From PayoutReleaseRequestedV1: transfers the payout to the seller's connected account. */
    public Mono<Payment> release(String orderId, long payoutCents, String commandId) {
        return payments.selectByPrimaryKey(orderId)
                .switchIfEmpty(Mono.error(() -> new CommandRejectedException(HttpStatus.NOT_FOUND, "payment-unknown", "No payment for order " + orderId)))
                .flatMap(p -> {
                    if (!"CONFIRMED".equals(p.getStatus())) {
                        return Mono.empty();   // already releasing / paid out (redelivery), or not confirmed: the aggregate would reject anyway
                    }
                    return accounts.selectByPrimaryKey(p.getSellerId()).map(AnnounceSellerAccount::getAccountRef).defaultIfEmpty("")
                            .flatMap(accountRef -> provider.release(p.getIntentRef(), payoutCents, p.getCurrency(), accountRef))
                            .flatMap(ref -> send(new RequestPayout(orderId, payoutCents, ref), commandId, Payment.class));
                });
    }

    /** From RefundRequestedV1. */
    public Mono<Payment> refund(String orderId, long amountCents, String reason, String commandId) {
        return payments.selectByPrimaryKey(orderId)
                .switchIfEmpty(Mono.error(() -> new CommandRejectedException(HttpStatus.NOT_FOUND, "payment-unknown", "No payment for order " + orderId)))
                .flatMap(p -> {
                    if (!"CONFIRMED".equals(p.getStatus())) {
                        return Mono.empty();
                    }
                    return provider.refund(p.getIntentRef(), amountCents, p.getCurrency(), reason)
                            .flatMap(ref -> send(new RequestRefund(orderId, amountCents, ref, reason), commandId, Payment.class));
                });
    }

    /** From OrderCancelledV1. */
    public Mono<Payment> cancel(String orderId, String commandId) {
        return payments.selectByPrimaryKey(orderId).flatMap(p -> {
            Mono<Void> atProvider = "INTENT_CREATED".equals(p.getStatus()) || "FAILED".equals(p.getStatus()) ? provider.cancelIntent(p.getIntentRef()) : Mono.empty();
            return atProvider.then(send(new CancelIntent(orderId), commandId, Payment.class));
        });
    }

    /**
     * The fake provider's webhook: {@code {providerEventId, kind, orderId | userId, ref?, reason?, status?, payoutsEnabled?}}
     * signed with HMAC-SHA256 over the raw body. Kinds: CONFIRMED, FAILED, PAYOUT_SENT, REFUND_SENT for a payment;
     * ACCOUNT_UPDATED for a seller account. A duplicate provider event id is acknowledged and ignored.
     */
    public Mono<Void> handleFakeWebhook(String rawBody, String signature) {
        if (!WebhookSignature.verify(properties.getFake().getSecret(), rawBody, signature)) {
            return Mono.error(new CommandRejectedException(HttpStatus.UNAUTHORIZED, "bad-signature", "Webhook signature does not match"));
        }
        JsonNode body;
        try {
            body = json.readTree(rawBody);
        } catch (Exception e) {
            return Mono.error(new IllegalArgumentException("Webhook body is not JSON"));
        }
        String eventId = text(body, "providerEventId");
        String kind = text(body, "kind");
        if (eventId == null || kind == null) {
            return Mono.error(new IllegalArgumentException("providerEventId and kind are required"));
        }
        String commandId = "webhook:" + eventId;
        if ("ACCOUNT_UPDATED".equals(kind)) {
            return send(new RecordAccountEvent(body.path("userId").asInt(), eventId, text(body, "status"), body.path("payoutsEnabled").asBoolean(false),
                    LocalDateTime.now()), commandId, SellerAccount.class).then();
        }
        ProviderEventKind providerKind = ProviderEventKind.valueOf(kind);
        return send(new RecordProviderEvent(text(body, "orderId"), eventId, providerKind, text(body, "ref"), text(body, "reason"), LocalDateTime.now()),
                commandId, Payment.class).then();
    }

    /** Starts (or resumes) onboarding at the provider; the fake provider verifies at once. */
    public Mono<SellerAccount> onboard(Integer userId, String commandId) {
        return provider.onboardSeller(userId)
                .flatMap(link -> send(new StartOnboarding(userId, provider.name(), link.accountRef(), link.onboardingUrl(), link.status(), link.payoutsEnabled()),
                        commandId, SellerAccount.class))
                .onErrorResume(CommandSubjectAlreadyExistsException.class, e -> Mono.error(
                        new CommandRejectedException(HttpStatus.CONFLICT, "already-onboarded", "Seller " + userId + " already has an account; GET it instead")));
    }

    public Mono<AnnounceSellerAccount> findAccount(Integer userId) {
        return accounts.selectByPrimaryKey(userId);
    }

    public Mono<AnnouncePayment> findPayment(String orderId) {
        return payments.selectByPrimaryKey(orderId);
    }

    private <R> Mono<R> send(com.opencqrs.framework.command.Command command, String commandId, Class<R> type) {
        Map<String, ?> metaData = commandId == null || commandId.isBlank() ? Map.of() : Map.of(MetaDataKeys.COMMAND_ID, commandId);
        return router.send(command, metaData, type);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
