package org.linlinjava.announce.payment.infrastructure.provider;

import org.linlinjava.announce.payment.domain.PaymentProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Stub of the Stripe Connect adapter (separate charges and transfers with {@code application_fee_amount}, Express
 * accounts for sellers). Selected with {@code payment.provider=stripe}; every call fails until the real integration
 * lands (a follow-up), so the stub cannot be mistaken for a working provider.
 */
@Component
@ConditionalOnProperty(prefix = "payment", name = "provider", havingValue = "stripe")
public class StripeConnectPaymentProvider implements PaymentProvider {

    @Override
    public String name() {
        return "STRIPE";
    }

    @Override
    public Mono<Intent> createIntent(IntentRequest request) {
        return Mono.error(new UnsupportedOperationException("Stripe Connect adapter is not implemented yet"));
    }

    @Override
    public Mono<String> release(String intentRef, long payoutCents, String currency, String sellerAccountRef) {
        return Mono.error(new UnsupportedOperationException("Stripe Connect adapter is not implemented yet"));
    }

    @Override
    public Mono<String> refund(String intentRef, long amountCents, String currency, String reason) {
        return Mono.error(new UnsupportedOperationException("Stripe Connect adapter is not implemented yet"));
    }

    @Override
    public Mono<Void> cancelIntent(String intentRef) {
        return Mono.error(new UnsupportedOperationException("Stripe Connect adapter is not implemented yet"));
    }

    @Override
    public Mono<AccountLink> onboardSeller(Integer userId) {
        return Mono.error(new UnsupportedOperationException("Stripe Connect adapter is not implemented yet"));
    }
}
