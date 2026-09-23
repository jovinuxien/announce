package org.linlinjava.announce.goods.domain.announce;

import java.util.List;

/** A sellable variant of a goods announce (announce_goods_product): specification values with their own price, stock and picture. */
public record Variant(List<String> specifications, Money price, int stock, String pictureUrl) {

    public Variant {
        specifications = specifications == null ? List.of() : List.copyOf(specifications);
        price = price == null ? Money.zero() : price;
        if (stock < 0) {
            throw new IllegalArgumentException("Stock must not be negative");
        }
        pictureUrl = pictureUrl == null ? "" : pictureUrl;
    }
}
