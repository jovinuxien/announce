package org.linlinjava.announce.payment.infrastructure.provider;

import org.linlinjava.announce.payment.domain.PaymentProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory provider for tests and local development: intents, transfers and refunds get fake references, every
 * seller is verified on onboarding with payouts enabled, and nothing happens asynchronously. Webhooks are simulated
 * through {@code POST /api/payments/webhook/fake}, signed with {@code payment.fake.secret}.
 */
@Component
@ConditionalOnProperty(prefix = "payment", name = "provider", havingValue = "fake", matchIfMissing = true)
public class FakePaymentProvider implements PaymentProvider {

    public record Call(String operation, String ref, long amountCents) {}

    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, IntentRequest> intents = new ConcurrentHashMap<>();
    private final List<Call> calls = new CopyOnWriteArrayList<>();

    @Override
    public String name() {
        return "FAKE";
    }

    @Override
    public Mono<Intent> createIntent(IntentRequest request) {
        return Mono.fromSupplier(() -> {
            String ref = "pi_fake_" + sequence.incrementAndGet();
            intents.put(ref, request);
            calls.add(new Call("createIntent", ref, request.amountCents()));
            return new Intent(ref, ref + "_secret");
        });
    }

    @Override
    public Mono<String> release(String intentRef, long payoutCents, String currency, String sellerAccountRef) {
        return Mono.fromSupplier(() -> {
            String ref = "tr_fake_" + sequence.incrementAndGet();
            calls.add(new Call("release", ref, payoutCents));
            return ref;
        });
    }

    @Override
    public Mono<String> refund(String intentRef, long amountCents, String currency, String reason) {
        return Mono.fromSupplier(() -> {
            String ref = "re_fake_" + sequence.incrementAndGet();
            calls.add(new Call("refund", ref, amountCents));
            return ref;
        });
    }

    @Override
    public Mono<Void> cancelIntent(String intentRef) {
        return Mono.fromRunnable(() -> calls.add(new Call("cancelIntent", intentRef, 0)));
    }

    @Override
    public Mono<AccountLink> onboardSeller(Integer userId) {
        return Mono.fromSupplier(() -> {
            String ref = "acct_fake_" + userId;
            calls.add(new Call("onboardSeller", ref, 0));
            return new AccountLink(ref, "https://fake-provider.invalid/onboarding/" + ref, "VERIFIED", true);
        });
    }

    /** Every provider call so far, for tests. */
    public List<Call> calls() {
        return List.copyOf(calls);
    }
}
