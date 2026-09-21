package org.linlinjava.announce.goods.domain.announce;

/** A fact about the item shown on the announce page (e.g. "Colour" / "Red"). Stored in announce_goods_attribute. */
public record Attribute(String name, String value) {
    public Attribute {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Attribute name is required");
        }
        name = name.trim();
        value = value == null ? "" : value.trim();
    }
}
