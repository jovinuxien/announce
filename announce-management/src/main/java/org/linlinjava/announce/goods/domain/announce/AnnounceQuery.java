package org.linlinjava.announce.goods.domain.announce;

import java.util.List;
import java.util.Map;

/**
 * Browse criteria over the announce read model. Page is 1-based. {@code sort} is one of {@link #SORTS}; anything
 * else falls back to newest. Status defaults to PUBLISHED so public listings never show drafts; a seller passes
 * their own id and a null status to see everything. {@code kind} null means every kind.
 */
public record AnnounceQuery(Kind kind,
                            List<Integer> categoryIds,
                            Integer brandId,
                            Integer sellerId,
                            Integer regionId,
                            String keywords,
                            SaleType saleType,
                            AnnounceStatus status,
                            int page,
                            int size,
                            String sort) {

    public static final int MAX_SIZE = 100;
    /** sort key -> SQL order by clause over the joined read model (a = announce, g = goods detail) */
    public static final Map<String, String> SORTS = Map.of(
            "newest", "a.add_time desc",
            "oldest", "a.add_time asc",
            "price_asc", "g.price asc",
            "price_desc", "g.price desc",
            "expiring", "a.expires_at asc");

    public AnnounceQuery {
        categoryIds = categoryIds == null ? List.of() : List.copyOf(categoryIds);
        keywords = keywords == null || keywords.isBlank() ? null : keywords.trim();
        page = Math.max(page, 1);
        size = size <= 0 ? 20 : Math.min(size, MAX_SIZE);
        sort = SORTS.containsKey(sort) ? sort : "newest";
    }

    public static AnnounceQuery published(Kind kind, int page, int size) {
        return new AnnounceQuery(kind, null, null, null, null, null, null, AnnounceStatus.PUBLISHED, page, size, "newest");
    }

    public long offset() {
        return (long) (page - 1) * size;
    }

    public String orderBy() {
        return SORTS.get(sort);
    }
}
