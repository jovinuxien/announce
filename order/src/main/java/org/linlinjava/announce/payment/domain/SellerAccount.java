package org.linlinjava.announce.payment.domain;

import org.linlinjava.announce.payment.domain.events.SellerAccountCreated;
import org.linlinjava.announce.payment.domain.events.SellerAccountUpdated;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/** State of the SellerAccount aggregate under {@code /seller-account/{userId}}: the seller's connected payout account at the provider. */
public record SellerAccount(Integer userId, String provider, String accountRef, String onboardingUrl, String status, boolean payoutsEnabled,
                            Set<String> providerEventIds, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public SellerAccount {
        providerEventIds = providerEventIds == null ? Set.of() : Set.copyOf(providerEventIds);
    }

    public boolean hasSeen(String providerEventId) {
        return providerEventId != null && providerEventIds.contains(providerEventId);
    }

    public static SellerAccount apply(Integer userId, SellerAccountCreated e) {
        return new SellerAccount(userId, e.provider(), e.accountRef(), e.onboardingUrl(), e.status(), e.payoutsEnabled(), Set.of(), e.at(), e.at());
    }

    public SellerAccount apply(SellerAccountUpdated e) {
        Set<String> ids = new LinkedHashSet<>(providerEventIds);
        if (e.providerEventId() != null) ids.add(e.providerEventId());
        return new SellerAccount(userId, provider, accountRef, onboardingUrl, e.status(), e.payoutsEnabled(), ids, createdAt, e.at());
    }
}
