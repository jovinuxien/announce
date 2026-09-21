package org.linlinjava.announce.goods.domain.category;

/** Identity of a category (announce_category.id). */
public record CategoryId(Integer value) {
    public CategoryId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("Category id must be a positive integer");
        }
    }
}
