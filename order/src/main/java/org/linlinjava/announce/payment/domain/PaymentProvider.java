package org.linlinjava.announce.payment.domain;

import reactor.core.publisher.Mono;

/**
 * The marketplace payment provider port ({@code doc/order-context.md} §4): the platform is merchant of record, the
 * provider holds the buyer's money, runs seller KYC and pays out. Adapters: {@code FakePaymentProvider} (tests and
 * local dev) and {@code StripeConnectPaymentProvider} (stub; separate charges and transfers with an application fee).
 */
public interface PaymentProvider {

    String name();

    record IntentRequest(String orderId, Integer buyerId, Integer sellerId, long amountCents, long feeCents, String currency) {}
    record Intent(String intentRef, String clientSecret) {}
    record AccountLink(String accountRef, String onboardingUrl, String status, boolean payoutsEnabled) {}

    /** Holds the buyer's payment for a deal; the client completes it with the client secret. */
    Mono<Intent> createIntent(IntentRequest request);

    /** Transfers the payout (price minus fee) to the seller's connected account; answers the transfer reference. */
    Mono<String> release(String intentRef, long payoutCents, String currency, String sellerAccountRef);

    /** Refunds the buyer; answers the refund reference. */
    Mono<String> refund(String intentRef, long amountCents, String currency, String reason);

    /** Voids an intent that was never completed. */
    Mono<Void> cancelIntent(String intentRef);

    /** Creates (or resumes) the seller's connected account and answers where to finish KYC. */
    Mono<AccountLink> onboardSeller(Integer userId);
}
