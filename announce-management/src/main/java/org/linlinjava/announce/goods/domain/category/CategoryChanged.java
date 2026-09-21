package org.linlinjava.announce.goods.domain.category;

import org.linlinjava.announce.goods.domain.model.events.LitemallDomainEvent;

/** The category tree changed (search rebuilds facets, marketing refreshes menus). */
public final class CategoryChanged extends LitemallDomainEvent {
    public enum Kind { CREATED, RENAMED, MOVED, HIDDEN, SHOWN }

    private final CategoryId categoryId;
    private final Kind kind;

    public CategoryChanged(CategoryId categoryId, Kind kind) {
        this.categoryId = categoryId;
        this.kind = kind;
    }

    public CategoryId categoryId() { return categoryId; }
    public Kind kind() { return kind; }

    @Override
    public String toString() {
        return "CategoryChanged[" + categoryId.value() + ", " + kind + "]";
    }
}
