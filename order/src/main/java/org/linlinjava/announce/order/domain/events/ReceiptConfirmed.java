package org.linlinjava.announce.order.domain.events;

import java.time.LocalDateTime;

/** The buyer confirmed receipt, which ends the dispute window early. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record ReceiptConfirmed(Integer buyerId,
                               LocalDateTime at) {
}
