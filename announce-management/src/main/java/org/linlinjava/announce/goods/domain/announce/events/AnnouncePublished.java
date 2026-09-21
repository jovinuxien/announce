package org.linlinjava.announce.goods.domain.announce.events;

import org.linlinjava.announce.goods.domain.announce.AnnounceId;
import org.linlinjava.announce.goods.domain.announce.Money;
import org.linlinjava.announce.goods.domain.announce.SaleType;
import org.linlinjava.announce.goods.domain.announce.SellerId;
import org.linlinjava.announce.goods.domain.category.CategoryId;

import java.time.LocalDateTime;

/** An announce became visible. Search indexes it; the auction context opens an auction when the sale type is AUCTION. */
public final class AnnouncePublished extends AnnounceEvent {
    private final CategoryId categoryId;
    private final SaleType saleType;
    private final Money price;
    private final LocalDateTime expiresAt;

    public AnnouncePublished(AnnounceId announceId, SellerId sellerId, CategoryId categoryId, SaleType saleType, Money price, LocalDateTime expiresAt) {
        super(announceId, sellerId);
        this.categoryId = categoryId;
        this.saleType = saleType;
        this.price = price;
        this.expiresAt = expiresAt;
    }

    public CategoryId categoryId() { return categoryId; }
    public SaleType saleType() { return saleType; }
    public Money price() { return price; }
    public LocalDateTime expiresAt() { return expiresAt; }
}
