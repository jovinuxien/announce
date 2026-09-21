package org.linlinjava.announce.goods.domain.announce;

import org.linlinjava.announce.goods.domain.brand.BrandId;
import org.linlinjava.announce.goods.domain.category.CategoryId;

import java.util.List;

/**
 * Everything a member fills in when drafting or editing an announce. Validated on construction so that an
 * {@link Announce} can never hold nonsense; the publish rules (price for a fixed-price sale, location) are
 * checked when publishing, because a draft may be incomplete.
 */
public record AnnounceDetails(String title,
                              String brief,
                              String description,
                              List<String> keywords,
                              List<String> gallery,
                              String unit,
                              CategoryId categoryId,
                              BrandId brandId,
                              SaleType saleType,
                              Money price,
                              Location location,
                              List<Attribute> attributes,
                              List<Specification> specifications) {

    public static final int TITLE_MAX = 127;
    public static final int BRIEF_MAX = 255;

    public AnnounceDetails {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title is required");
        }
        title = title.trim();
        if (title.length() > TITLE_MAX) {
            throw new IllegalArgumentException("Title is longer than " + TITLE_MAX + " characters");
        }
        brief = brief == null ? "" : brief.trim();
        if (brief.length() > BRIEF_MAX) {
            throw new IllegalArgumentException("Brief is longer than " + BRIEF_MAX + " characters");
        }
        description = description == null ? "" : description;
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        gallery = gallery == null ? List.of() : List.copyOf(gallery);
        unit = unit == null || unit.isBlank() ? "pcs" : unit.trim();
        if (categoryId == null) {
            throw new IllegalArgumentException("Category is required");
        }
        saleType = saleType == null ? SaleType.FIXED_PRICE : saleType;
        price = price == null ? Money.zero() : price;
        if (saleType == SaleType.GIVEAWAY && !price.isZero()) {
            throw new IllegalArgumentException("A giveaway has no price");
        }
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
        specifications = specifications == null ? List.of() : List.copyOf(specifications);
    }

    public String mainPicture() {
        return gallery.isEmpty() ? "" : gallery.get(0);
    }
}
