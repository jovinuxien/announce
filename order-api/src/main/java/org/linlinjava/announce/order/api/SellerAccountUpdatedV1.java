package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;
import java.time.LocalDateTime;

/** A seller's connected payout account changed at the provider; {@code payoutsEnabled} gates AcceptPurchase and OpenAuction. */
@PublicEvent(type = "seller-account.updated", version = 1)
public record SellerAccountUpdatedV1(Integer userId,
                                     String provider,
                                     String accountRef,
                                     String status,
                                     boolean payoutsEnabled,
                                     LocalDateTime at) {
}
