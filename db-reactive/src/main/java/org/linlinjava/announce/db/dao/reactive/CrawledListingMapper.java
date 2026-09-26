package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.CrawledListing;
import org.linlinjava.announce.db.domain.CrawledListingExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Reactive (R2DBC) mapper for {@code crawled_listing}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link CrawledListingExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/CrawledListingMapper.xml}.
 */
public interface CrawledListingMapper {
    Mono<Long> countByExample(CrawledListingExample example);
    Mono<Integer> deleteByExample(CrawledListingExample example);
    Mono<Integer> deleteByPrimaryKey(String id);
    Mono<Integer> insert(CrawledListing record);
    Mono<Integer> insertSelective(CrawledListing record);
    Mono<CrawledListing> selectOneByExample(CrawledListingExample example);
    Mono<CrawledListing> selectOneByExampleSelective(@Param("example") CrawledListingExample example, @Param("selective") CrawledListing.Column ... selective);
    Flux<CrawledListing> selectByExampleSelective(@Param("example") CrawledListingExample example, @Param("selective") CrawledListing.Column ... selective);
    Flux<CrawledListing> selectByExample(CrawledListingExample example);
    Mono<CrawledListing> selectByPrimaryKeySelective(@Param("id") String id, @Param("selective") CrawledListing.Column ... selective);
    Mono<CrawledListing> selectByPrimaryKey(String id);
    Mono<Integer> updateByExampleSelective(@Param("record") CrawledListing record, @Param("example") CrawledListingExample example);
    Mono<Integer> updateByExample(@Param("record") CrawledListing record, @Param("example") CrawledListingExample example);
    Mono<Integer> updateByPrimaryKeySelective(CrawledListing record);
    Mono<Integer> updateByPrimaryKey(CrawledListing record);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<CrawledListing> selectPageByExample(@Param("example") CrawledListingExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<CrawledListing> selectPageByExampleSelective(@Param("example") CrawledListingExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") CrawledListing.Column... selective);

    /** Inserts the row or overwrites every content column of the existing one; first_seen_at and click_count are kept. */
    Mono<Integer> upsert(CrawledListing record);

    /** Records that the listing was seen again without changes: last_seen_at (and update_time) only. */
    Mono<Integer> touchSeen(@Param("id") String id, @Param("seenAt") LocalDateTime seenAt);

    /** Sets status and update_time on one row. */
    Mono<Integer> updateStatus(@Param("id") String id, @Param("status") String status, @Param("updateTime") LocalDateTime updateTime);

    /** Ids of ACTIVE rows not seen since {@code before}, oldest first, at most {@code limit}. */
    Flux<String> selectStaleIds(@Param("before") LocalDateTime before, @Param("limit") int limit);

    /** Adds one to click_count. */
    Mono<Integer> incrementClickCount(@Param("id") String id, @Param("updateTime") LocalDateTime updateTime);
}
