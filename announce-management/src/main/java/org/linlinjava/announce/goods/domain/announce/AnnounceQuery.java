package org.linlinjava.announce.goods.domain.announce;

import java.util.List;
import java.util.Map;

/**
 * Browse criteria for the catalog. Page is 1-based. {@code sort} is one of {@link #SORTS}; anything else falls back to newest.
 * Status defaults to PUBLISHED so public listings never show drafts; a seller passes their own id and a null status to see everything.
 */
public record AnnounceQuery(List<Integer> categoryIds,
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
    /** sort key -> SQL order by clause on announce_goods */
    public static final Map<String, String> SORTS = Map.of(
            "newest", "add_time desc",
            "oldest", "add_time asc",
            "price_asc", "retail_price asc",
            "price_desc", "retail_price desc",
            "expiring", "expire_time asc");

    public AnnounceQuery {
        categoryIds = categoryIds == null ? List.of() : List.copyOf(categoryIds);
        keywords = keywords == null || keywords.isBlank() ? null : keywords.trim();
        page = Math.max(page, 1);
        size = size <= 0 ? 20 : Math.min(size, MAX_SIZE);
        sort = SORTS.containsKey(sort) ? sort : "newest";
    }

    public static AnnounceQuery published(int page, int size) {
        return new AnnounceQuery(null, null, null, null, null, null, AnnounceStatus.PUBLISHED, page, size, "newest");
    }

    public long offset() {
        return (long) (page - 1) * size;
    }

    public String orderBy() {
        return SORTS.get(sort);
    }
}
