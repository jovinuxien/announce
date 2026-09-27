package org.linlinjava.announce.payment.domain.events;

import java.time.LocalDateTime;

/** The provider reported a change of the connected account (KYC outcome, restriction). Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record SellerAccountUpdated(String providerEventId,
                                   String status,
                                   boolean payoutsEnabled,
                                   LocalDateTime at) {
}
