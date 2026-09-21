package org.linlinjava.announce.goods.domain.category;

import org.linlinjava.announce.goods.domain.announce.AnnounceRuleViolation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A node of the two-level category tree (announce_category): roots ("L1") and their children ("L2").
 * Its own aggregate: it changes when the site taxonomy changes, never because an announce changed.
 */
public final class Category {

    public static final String ROOT_LEVEL = "L1";
    public static final String CHILD_LEVEL = "L2";

    private CategoryId id;
    private String name;
    private CategoryId parentId;
    private String keywords;
    private String description;
    private String iconUrl;
    private String pictureUrl;
    private int sortOrder;
    private boolean hidden;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private final List<CategoryChanged> events = new ArrayList<>();

    private Category(CategoryId id, String name, CategoryId parentId, String keywords, String description, String iconUrl,
                     String pictureUrl, int sortOrder, boolean hidden, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = validName(name);
        this.parentId = parentId;
        this.keywords = keywords == null ? "" : keywords;
        this.description = description == null ? "" : description;
        this.iconUrl = iconUrl == null ? "" : iconUrl;
        this.pictureUrl = pictureUrl == null ? "" : pictureUrl;
        this.sortOrder = sortOrder;
        this.hidden = hidden;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Category root(String name, String description, String iconUrl, int sortOrder, LocalDateTime now) {
        Category c = new Category(null, name, null, "", description, iconUrl, "", sortOrder, false, now, now);
        return c;
    }

    /** Children hang under a root only: the tree has two levels. */
    public static Category childOf(Category parent, String name, String description, String iconUrl, int sortOrder, LocalDateTime now) {
        if (!parent.isRoot()) {
            throw new AnnounceRuleViolation("Category " + parent.name + " is not a root; the tree has two levels");
        }
        return new Category(null, name, parent.id, "", description, iconUrl, "", sortOrder, false, now, now);
    }

    public static Category rehydrate(CategoryId id, String name, CategoryId parentId, String keywords, String description, String iconUrl,
                                     String pictureUrl, int sortOrder, boolean hidden, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new Category(Objects.requireNonNull(id, "id"), name, parentId, keywords, description, iconUrl, pictureUrl, sortOrder, hidden, createdAt, updatedAt);
    }

    public void rename(String newName, LocalDateTime now) {
        this.name = validName(newName);
        touch(now, CategoryChanged.Kind.RENAMED);
    }

    public void describe(String description, String iconUrl, String pictureUrl, String keywords, int sortOrder, LocalDateTime now) {
        this.description = description == null ? "" : description;
        this.iconUrl = iconUrl == null ? this.iconUrl : iconUrl;
        this.pictureUrl = pictureUrl == null ? this.pictureUrl : pictureUrl;
        this.keywords = keywords == null ? this.keywords : keywords;
        this.sortOrder = sortOrder;
        touch(now, CategoryChanged.Kind.RENAMED);
    }

    /** Moves a child under another root. Roots cannot be moved. */
    public void moveUnder(Category newParent, LocalDateTime now) {
        if (isRoot()) {
            throw new AnnounceRuleViolation("A root category cannot be moved");
        }
        if (!newParent.isRoot()) {
            throw new AnnounceRuleViolation("Category " + newParent.name + " is not a root");
        }
        if (newParent.id.equals(id)) {
            throw new AnnounceRuleViolation("A category cannot be its own parent");
        }
        this.parentId = newParent.id;
        touch(now, CategoryChanged.Kind.MOVED);
    }

    public void hide(LocalDateTime now) {
        if (!hidden) {
            hidden = true;
            touch(now, CategoryChanged.Kind.HIDDEN);
        }
    }

    public void show(LocalDateTime now) {
        if (hidden) {
            hidden = false;
            touch(now, CategoryChanged.Kind.SHOWN);
        }
    }

    public void assignId(CategoryId newId) {
        if (id != null) {
            throw new IllegalStateException("Category already has an id");
        }
        id = Objects.requireNonNull(newId, "id");
        events.add(new CategoryChanged(id, CategoryChanged.Kind.CREATED));
    }

    public List<CategoryChanged> pullEvents() {
        List<CategoryChanged> pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }

    private void touch(LocalDateTime now, CategoryChanged.Kind kind) {
        updatedAt = now;
        if (id != null) {
            events.add(new CategoryChanged(id, kind));
        }
    }

    private static String validName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name is required");
        }
        if (name.trim().length() > 63) {
            throw new IllegalArgumentException("Category name is longer than 63 characters");
        }
        return name.trim();
    }

    public boolean isRoot() { return parentId == null; }
    public String level() { return isRoot() ? ROOT_LEVEL : CHILD_LEVEL; }
    public CategoryId id() { return id; }
    public String name() { return name; }
    public CategoryId parentId() { return parentId; }
    public String keywords() { return keywords; }
    public String description() { return description; }
    public String iconUrl() { return iconUrl; }
    public String pictureUrl() { return pictureUrl; }
    public int sortOrder() { return sortOrder; }
    public boolean isHidden() { return hidden; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }
}
