package org.linlinjava.announce.order.domain.events;

import java.time.LocalDateTime;

/** Staff resolved the dispute: RELEASE the payout or REFUND the buyer. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record DisputeResolved(Integer adminId,
                              String resolution,
                              String note,
                              LocalDateTime at) {
}
