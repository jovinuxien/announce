package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Counts for the admin dashboard (gateway-admin). Hand-written SQL in {@code AdminDashboardMapper.xml}; rows are
 * keyed by column alias.
 */
public interface AdminDashboardMapper {
    /** kind, status, total over {@code announce} (not deleted). */
    Flux<Map<String, Object>> announceCounts();

    /** source, status, total over {@code crawled_listing}. */
    Flux<Map<String, Object>> listingCounts();

    Mono<Long> memberCount();

    Mono<Long> adminCount();

    Mono<Long> categoryCount();

    Mono<Long> brandCount();

    /** day (yyyy-MM-dd), total: members registered per day over the last {@code days} days. */
    Flux<Map<String, Object>> newMembersPerDay(@Param("days") int days);

    /** day (yyyy-MM-dd), total: announces posted per day over the last {@code days} days. */
    Flux<Map<String, Object>> newAnnouncesPerDay(@Param("days") int days);

    Mono<Long> listingClickTotal();
}
