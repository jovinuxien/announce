package org.linlinjava.announce.goods.domain.announce;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.linlinjava.announce.goods.domain.brand.BrandId;

import java.util.List;

/**
 * An item for sale, auction or giveaway: the only biddable kind. Variants, attributes and specifications are kept
 * from the mall model; a plain classified has none of them.
 */
public record GoodsDetails(Money price,
                           Condition condition,
                           SaleType saleType,
                           BrandId brandId,
                           String unit,
                           List<Variant> variants,
                           List<Attribute> attributes,
                           List<Specification> specifications) implements AnnounceKind {

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
        return new GoodsDetails(price, condition, SaleType.FIXED_PRICE, null, null, null, null, null);
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
