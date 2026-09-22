package org.linlinjava.announce.goods.domain.announce;

import org.linlinjava.announce.goods.domain.category.CategoryId;

import java.util.List;

/**
 * What every kind of announce shares and the member edits: title, texts, pictures, keywords, category and where it
 * is. Validated on construction; the publish rules (a location) are checked when publishing, because a draft may
 * be incomplete.
 */
public record AnnounceContent(String title,
                              String brief,
                              String description,
                              List<String> keywords,
                              List<String> gallery,
                              CategoryId categoryId,
                              Location location) {

    public static final int TITLE_MAX = 127;
    public static final int BRIEF_MAX = 255;

    public AnnounceContent {
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
        if (categoryId == null) {
            throw new IllegalArgumentException("Category is required");
        }
    }

    public String mainPicture() {
        return gallery.isEmpty() ? "" : gallery.get(0);
    }
}
