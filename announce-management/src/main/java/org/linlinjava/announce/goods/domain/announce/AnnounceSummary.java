package org.linlinjava.announce.goods.domain.announce;

import org.linlinjava.announce.goods.domain.category.CategoryId;

import java.time.LocalDateTime;

/**
 * What a listing page shows per announce, read from the {@code announce} table joined with the detail table of its
 * kind: the shared columns plus the few kind-specific ones a list needs. {@code kindFacts} holds those:
 * price, sale type, condition and the auction end for goods; employer, contract type, deadline for jobs; start, end,
 * ticketed for events.
 */
public record AnnounceSummary(String id,
                              Kind kind,
                              SellerId sellerId,
                              String title,
                              String brief,
                              String pictureUrl,
                              AnnounceStatus status,
                              CategoryId categoryId,
                              Location location,
                              LocalDateTime createdAt,
                              LocalDateTime publishedAt,
                              LocalDateTime expiresAt,
                              KindFacts kindFacts,
                              Moderation moderation) {

    /** Set when staff withdrew or expired the announce (admin listing); null otherwise. */
    public record Moderation(Integer moderatedBy, String reason) {
    }

    /** The kind-specific columns of a summary; fields of the other kinds are null. */
    public record KindFacts(Money price, SaleType saleType, Condition condition,
                            String employer, ContractType contractType, LocalDateTime applicationDeadline,
                            LocalDateTime startsAt, LocalDateTime endsAt, Boolean ticketed, Money ticketPrice,
                            LocalDateTime auctionStartsAt, LocalDateTime auctionEndsAt) {
    }
}
