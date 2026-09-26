package org.linlinjava.announce.payment.interfaces.rest;

import org.linlinjava.announce.db.domain.AnnouncePayment;
import org.linlinjava.announce.db.domain.AnnounceSellerAccount;
import org.linlinjava.announce.payment.application.PaymentService;
import org.linlinjava.announce.payment.domain.SellerAccount;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Provider webhooks ({@code /api/payments/webhook/{provider}}), the buyer's view of a deal's payment (client secret)
 * and seller onboarding ({@code /api/sellers/me/account}). Member from {@code X-User-Id}.
 */
@RestController
public class PaymentController {

    public static final String USER_HEADER = "X-User-Id";
    public static final String COMMAND_ID_HEADER = "X-Command-Id";
    public static final String FAKE_SIGNATURE_HEADER = "X-Fake-Signature";

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    /** Body: the fake provider's event as JSON; header {@code X-Fake-Signature}: HMAC-SHA256 hex of the raw body with {@code payment.fake.secret}. */
    @PostMapping("/api/payments/webhook/fake")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> fakeWebhook(@RequestBody String body, @RequestHeader(value = FAKE_SIGNATURE_HEADER, required = false) String signature) {
        return payments.handleFakeWebhook(body, signature);
    }

    @PostMapping("/api/payments/webhook/stripe")
    public Mono<Void> stripeWebhook(@RequestBody String body) {
        return Mono.error(new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Stripe Connect webhooks are not implemented yet"));
    }

    /** The buyer's (or seller's) view of a deal's payment, including the client secret the buyer needs to pay. */
    @GetMapping("/api/orders/{id}/payment")
    public Mono<AnnouncePayment> payment(@PathVariable String id, @RequestHeader(USER_HEADER) Integer user) {
        return payments.findPayment(id)
                .filter(p -> user.equals(p.getBuyerId()) || user.equals(p.getSellerId()))
                .switchIfEmpty(Mono.error(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No payment for order " + id)));
    }

    @PostMapping("/api/sellers/me/account")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<SellerAccount> onboard(@RequestHeader(USER_HEADER) Integer user, @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId) {
        return payments.onboard(user, commandId);
    }

    @GetMapping("/api/sellers/me/account")
    public Mono<AnnounceSellerAccount> account(@RequestHeader(USER_HEADER) Integer user) {
        return payments.findAccount(user)
                .switchIfEmpty(Mono.error(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seller " + user + " has no payout account yet")));
    }
}
