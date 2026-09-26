package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;
import java.time.LocalDateTime;

/** The release rule passed (paid, delivered or dispatched per configuration, no dispute, grace elapsed or receipt confirmed); the payment context transfers the payout. */
@PublicEvent(type = "order.payout-release-requested", version = 1)
public record PayoutReleaseRequestedV1(String orderId,
                                       Integer sellerId,
                                       long payoutCents,
                                       String currency,
                                       LocalDateTime at) {
}
