package org.linlinjava.announce.messaging.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;

import java.time.LocalDateTime;

/**
 * The seller accepted the buyer's price proposal in a listing conversation: the "chat offer" door of the order
 * context (see {@code doc/order-context.md} section 1 and 9). {@code order} handles it with CreateFromOffer.
 *
 * @param conversationId the listing conversation the offer was made in
 * @param announceId     the announce being sold
 * @param buyerId        member who made the offer ({@code announce_user.id})
 * @param sellerId       member who accepted it, the owner of the announce
 * @param amountCents    agreed price in minor units
 * @param currency       ISO 4217 code, e.g. SEK
 * @param acceptedAt     when the seller accepted
 */
@PublicEvent(type = "messaging.offer-accepted", version = 1)
public record OfferAcceptedV1(String conversationId,
                              String announceId,
                              Integer buyerId,
                              Integer sellerId,
                              long amountCents,
                              String currency,
                              LocalDateTime acceptedAt) {
}
