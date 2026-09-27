package org.linlinjava.announce.order.domain.events;

import java.time.LocalDateTime;

/** The release rule passed; the payment context transfers the payout. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record PayoutReleaseRequested(long payoutCents,
                                     String currency,
                                     LocalDateTime at) {
}
