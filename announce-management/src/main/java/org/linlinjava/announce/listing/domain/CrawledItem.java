package org.linlinjava.announce.listing.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The normalised shape a feed parser produces for one crawled listing, independent of the site's field names.
 * Invariants that every source must respect live here: the brief is a snippet of at most {@link #BRIEF_LENGTH}
 * characters (never the full text), the attributes are name -> value strings in a stable order, the source,
 * external id, url and title are present.
 */
public record CrawledItem(ListingSource source,
                          String externalId,
                          String url,
                          ListingKind kind,
                          String title,
                          String brief,
                          String imageUrl,
                          BigDecimal price,
                          String currency,
                          ListingSaleType saleType,
                          LocalDateTime endsAt,
                          String sourceCategory,
                          Map<String, String> attributes,
                          String addressRaw,
                          LocalDateTime publishedAt) {

    public static final int BRIEF_LENGTH = 255;
    public static final int TITLE_LENGTH = 255;

    public CrawledItem {
        Objects.requireNonNull(source, "source");
        require(externalId, "externalId");
        require(url, "url");
        require(title, "title");
        kind = kind == null ? ListingKind.GOODS : kind;
        saleType = saleType == null ? ListingSaleType.FIXED_PRICE : saleType;
        title = truncate(title.strip(), TITLE_LENGTH);
        brief = truncate(brief == null ? "" : brief.strip(), BRIEF_LENGTH);
        attributes = attributes == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(attributes));
    }

    /** {@code {source}-{external_id}}: the row id and the index document id, the same on every run. */
    public String id() {
        return source.facetValue() + "-" + externalId;
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
