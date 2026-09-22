package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.AnnounceEventDetail;
import org.linlinjava.announce.db.domain.AnnounceEventDetailExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_event}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link AnnounceEventDetailExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/AnnounceEventDetailMapper.xml}.
 */
public interface AnnounceEventDetailMapper {
    Mono<Long> countByExample(AnnounceEventDetailExample example);
    Mono<Integer> deleteByExample(AnnounceEventDetailExample example);
    Mono<Integer> deleteByPrimaryKey(String announceId);
    Mono<Integer> insert(AnnounceEventDetail record);
    Mono<Integer> insertSelective(AnnounceEventDetail record);
    Mono<AnnounceEventDetail> selectOneByExample(AnnounceEventDetailExample example);
    Mono<AnnounceEventDetail> selectOneByExampleSelective(@Param("example") AnnounceEventDetailExample example, @Param("selective") AnnounceEventDetail.Column ... selective);
    Flux<AnnounceEventDetail> selectByExampleSelective(@Param("example") AnnounceEventDetailExample example, @Param("selective") AnnounceEventDetail.Column ... selective);
    Flux<AnnounceEventDetail> selectByExample(AnnounceEventDetailExample example);
    Mono<AnnounceEventDetail> selectByPrimaryKeySelective(@Param("announceId") String announceId, @Param("selective") AnnounceEventDetail.Column ... selective);
    Mono<AnnounceEventDetail> selectByPrimaryKey(String announceId);
    Mono<Integer> updateByExampleSelective(@Param("record") AnnounceEventDetail record, @Param("example") AnnounceEventDetailExample example);
    Mono<Integer> updateByExample(@Param("record") AnnounceEventDetail record, @Param("example") AnnounceEventDetailExample example);
    Mono<Integer> updateByPrimaryKeySelective(AnnounceEventDetail record);
    Mono<Integer> updateByPrimaryKey(AnnounceEventDetail record);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceEventDetail> selectPageByExample(@Param("example") AnnounceEventDetailExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceEventDetail> selectPageByExampleSelective(@Param("example") AnnounceEventDetailExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") AnnounceEventDetail.Column... selective);

    /** Inserts the row or overwrites every column of the existing one (projections replay events at least once). */
    Mono<Integer> upsert(AnnounceEventDetail record);
}
