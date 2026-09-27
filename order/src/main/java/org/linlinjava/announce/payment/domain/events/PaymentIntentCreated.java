package org.linlinjava.announce.payment.domain.events;

import java.time.LocalDateTime;

/** The provider holds an intent for the deal; the buyer completes it with the client secret. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record PaymentIntentCreated(String announceId,
                                   Integer buyerId,
                                   Integer sellerId,
                                   String provider,
                                   String intentRef,
                                   String clientSecret,
                                   long amountCents,
                                   long feeCents,
                                   long payoutCents,
                                   String currency,
                                   LocalDateTime at) {
}
