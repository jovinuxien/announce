package org.linlinjava.announce.goods.domain.announce;

import org.linlinjava.announce.goods.domain.category.CategoryId;

import java.time.LocalDateTime;

/** What a listing page shows per announce (no description, no children). Read side of the catalog. */
public record AnnounceSummary(AnnounceId id,
                              SellerId sellerId,
                              String title,
                              String brief,
                              String pictureUrl,
                              Money price,
                              SaleType saleType,
                              AnnounceStatus status,
                              CategoryId categoryId,
                              Location location,
                              LocalDateTime createdAt,
                              LocalDateTime expiresAt) {
}
