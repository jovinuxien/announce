package org.linlinjava.announce.payment.domain.events;

import java.time.LocalDateTime;

/** A seller started onboarding at the provider (connected account). Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record SellerAccountCreated(String provider,
                                   String accountRef,
                                   String onboardingUrl,
                                   String status,
                                   boolean payoutsEnabled,
                                   LocalDateTime at) {
}
