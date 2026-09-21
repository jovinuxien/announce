package org.linlinjava.announce.goods.domain.brand;

/** Identity of a brand / manufacturer (announce_brand.id). */
public record BrandId(Integer value) {
    public BrandId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("Brand id must be a positive integer");
        }
    }
}
