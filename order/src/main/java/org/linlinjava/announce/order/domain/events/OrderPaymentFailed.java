package org.linlinjava.announce.order.domain.events;

import java.time.LocalDateTime;

/** A payment attempt failed; the order stays payable until the pay window ends. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record OrderPaymentFailed(String reason,
                                 LocalDateTime at) {
}
