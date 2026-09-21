package org.linlinjava.announce.goods.domain.announce;

/** A selectable option of the item (e.g. "Size" / "M") with an optional picture. Stored in announce_goods_specification. */
public record Specification(String name, String value, String pictureUrl) {
    public Specification {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Specification name is required");
        }
        name = name.trim();
        value = value == null ? "" : value.trim();
        pictureUrl = pictureUrl == null ? "" : pictureUrl.trim();
    }
}
