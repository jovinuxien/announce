package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.AnnounceBase;
import org.linlinjava.announce.db.domain.AnnounceBaseExample;
import org.linlinjava.announce.db.domain.AnnounceSummaryRow;

import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link AnnounceBaseExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/AnnounceBaseMapper.xml}.
 */
public interface AnnounceBaseMapper {
    Mono<Long> countByExample(AnnounceBaseExample example);
    Mono<Integer> deleteByExample(AnnounceBaseExample example);
    Mono<Integer> deleteByPrimaryKey(String id);
    Mono<Integer> insert(AnnounceBase record);
    Mono<Integer> insertSelective(AnnounceBase record);
    Mono<AnnounceBase> selectOneByExample(AnnounceBaseExample example);
    Mono<AnnounceBase> selectOneByExampleSelective(@Param("example") AnnounceBaseExample example, @Param("selective") AnnounceBase.Column ... selective);
    Mono<AnnounceBase> selectOneByExampleWithBLOBs(AnnounceBaseExample example);
    Flux<AnnounceBase> selectByExampleSelective(@Param("example") AnnounceBaseExample example, @Param("selective") AnnounceBase.Column ... selective);
    Flux<AnnounceBase> selectByExampleWithBLOBs(AnnounceBaseExample example);
    Flux<AnnounceBase> selectByExample(AnnounceBaseExample example);
    Mono<AnnounceBase> selectByPrimaryKeySelective(@Param("id") String id, @Param("selective") AnnounceBase.Column ... selective);
    Mono<AnnounceBase> selectByPrimaryKey(String id);
    Mono<AnnounceBase> selectByPrimaryKeyWithLogicalDelete(@Param("id") String id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") AnnounceBase record, @Param("example") AnnounceBaseExample example);
    Mono<Integer> updateByExampleWithBLOBs(@Param("record") AnnounceBase record, @Param("example") AnnounceBaseExample example);
    Mono<Integer> updateByExample(@Param("record") AnnounceBase record, @Param("example") AnnounceBaseExample example);
    Mono<Integer> updateByPrimaryKeySelective(AnnounceBase record);
    Mono<Integer> updateByPrimaryKeyWithBLOBs(AnnounceBase record);
    Mono<Integer> updateByPrimaryKey(AnnounceBase record);
    Mono<Integer> logicalDeleteByExample(@Param("example") AnnounceBaseExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(String id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceBase> selectPageByExample(@Param("example") AnnounceBaseExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceBase> selectPageByExampleSelective(@Param("example") AnnounceBaseExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") AnnounceBase.Column... selective);

    /** Inserts the row or overwrites every column of the existing one (projections replay events at least once). */
    Mono<Integer> upsert(AnnounceBase record);

    /** Rewrites the content columns only (title, texts, gallery, keywords, category, location); status and dates stay. */
    Mono<Integer> updateContent(AnnounceBase record);

    /** Rewrites status, last_event_id and update_time; published_at and expires_at only when given. */
    Mono<Integer> updateStatus(AnnounceBase record);

    /**
     * Listing over announce joined with the detail table of every kind. Every criterion may be null; {@code orderBy}
     * is an SQL fragment over the aliases {@code a} (announce) and {@code g} (goods detail) and must come from a
     * whitelist, never from user input.
     */
    Flux<AnnounceSummaryRow> selectSummaries(@Param("kind") String kind,
                                             @Param("categoryIds") List<Integer> categoryIds,
                                             @Param("brandId") Integer brandId,
                                             @Param("sellerId") Integer sellerId,
                                             @Param("regionId") Integer regionId,
                                             @Param("keywords") String keywords,
                                             @Param("saleType") String saleType,
                                             @Param("status") String status,
                                             @Param("orderBy") String orderBy,
                                             @Param("offset") long offset,
                                             @Param("limit") int limit);

    Mono<Long> countSummaries(@Param("kind") String kind,
                              @Param("categoryIds") List<Integer> categoryIds,
                              @Param("brandId") Integer brandId,
                              @Param("sellerId") Integer sellerId,
                              @Param("regionId") Integer regionId,
                              @Param("keywords") String keywords,
                              @Param("saleType") String saleType,
                              @Param("status") String status);
}
