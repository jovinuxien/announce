package org.linlinjava.announce.goods.domain.announce;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.linlinjava.announce.goods.domain.brand.BrandId;

import java.time.LocalDateTime;
import java.util.List;

/**
 * An item for sale, auction or giveaway: the only biddable kind. Variants, attributes and specifications are kept
 * from the mall model; a plain classified has none of them. {@code auctionStartsAt} / {@code auctionEndsAt} are the
 * seller's schedule for an AUCTION sale (both optional on a draft; {@link AnnounceRules#resolveSchedule} fills them
 * at publish, so a published auction always carries both). The schedule rules live in {@link AnnounceRules}.
 */
public record GoodsDetails(Money price,
                           Condition condition,
                           SaleType saleType,
                           BrandId brandId,
                           String unit,
                           List<Variant> variants,
                           List<Attribute> attributes,
                           List<Specification> specifications,
                           LocalDateTime auctionStartsAt,
                           LocalDateTime auctionEndsAt) implements AnnounceKind {

    public static final int MAX_VARIANTS = 20;

    public GoodsDetails {
        saleType = saleType == null ? SaleType.FIXED_PRICE : saleType;
        price = price == null ? Money.zero() : price;
        if (saleType == SaleType.GIVEAWAY && !price.isZero()) {
            throw new IllegalArgumentException("A giveaway has no price");
        }
        unit = unit == null || unit.isBlank() ? "pcs" : unit.trim();
        variants = variants == null ? List.of() : List.copyOf(variants);
        if (variants.size() > MAX_VARIANTS) {
            throw new IllegalArgumentException("A goods announce has at most " + MAX_VARIANTS + " variants");
        }
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
        specifications = specifications == null ? List.of() : List.copyOf(specifications);
    }

    public static GoodsDetails fixedPrice(Money price, Condition condition) {
        return new GoodsDetails(price, condition, SaleType.FIXED_PRICE, null, null, null, null, null, null, null);
    }

    /** The same goods with another auction schedule (what publish writes once the defaults are resolved). */
    public GoodsDetails withSchedule(LocalDateTime startsAt, LocalDateTime endsAt) {
        return new GoodsDetails(price, condition, saleType, brandId, unit, variants, attributes, specifications, startsAt, endsAt);
    }

    /** True when the seller set at least one of the two auction dates. */
    @JsonIgnore
    public boolean hasSchedule() {
        return auctionStartsAt != null || auctionEndsAt != null;
    }

    @Override
    public Kind kind() {
        return Kind.GOODS;
    }

    @Override
    @JsonIgnore
    public boolean isBiddable() {
        return saleType == SaleType.AUCTION;
    }

    /** True when the item has a price to show: its own or one on a variant. */
    public boolean hasPrice() {
        return !price.isZero() || !variants.isEmpty();
    }
}
