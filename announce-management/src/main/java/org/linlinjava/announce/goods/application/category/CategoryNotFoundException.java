package org.linlinjava.announce.goods.application.category;

import org.linlinjava.announce.goods.domain.category.CategoryId;

public class CategoryNotFoundException extends RuntimeException {
    public CategoryNotFoundException(CategoryId id) {
        super("Category " + id.value() + " does not exist");
    }
}
