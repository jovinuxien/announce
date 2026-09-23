package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.AnnounceJobDetail;
import org.linlinjava.announce.db.domain.AnnounceJobDetailExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_job}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link AnnounceJobDetailExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/AnnounceJobDetailMapper.xml}.
 */
public interface AnnounceJobDetailMapper {
    Mono<Long> countByExample(AnnounceJobDetailExample example);
    Mono<Integer> deleteByExample(AnnounceJobDetailExample example);
    Mono<Integer> deleteByPrimaryKey(String announceId);
    Mono<Integer> insert(AnnounceJobDetail record);
    Mono<Integer> insertSelective(AnnounceJobDetail record);
    Mono<AnnounceJobDetail> selectOneByExample(AnnounceJobDetailExample example);
    Mono<AnnounceJobDetail> selectOneByExampleSelective(@Param("example") AnnounceJobDetailExample example, @Param("selective") AnnounceJobDetail.Column ... selective);
    Flux<AnnounceJobDetail> selectByExampleSelective(@Param("example") AnnounceJobDetailExample example, @Param("selective") AnnounceJobDetail.Column ... selective);
    Flux<AnnounceJobDetail> selectByExample(AnnounceJobDetailExample example);
    Mono<AnnounceJobDetail> selectByPrimaryKeySelective(@Param("announceId") String announceId, @Param("selective") AnnounceJobDetail.Column ... selective);
    Mono<AnnounceJobDetail> selectByPrimaryKey(String announceId);
    Mono<Integer> updateByExampleSelective(@Param("record") AnnounceJobDetail record, @Param("example") AnnounceJobDetailExample example);
    Mono<Integer> updateByExample(@Param("record") AnnounceJobDetail record, @Param("example") AnnounceJobDetailExample example);
    Mono<Integer> updateByPrimaryKeySelective(AnnounceJobDetail record);
    Mono<Integer> updateByPrimaryKey(AnnounceJobDetail record);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceJobDetail> selectPageByExample(@Param("example") AnnounceJobDetailExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceJobDetail> selectPageByExampleSelective(@Param("example") AnnounceJobDetailExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") AnnounceJobDetail.Column... selective);

    /** Inserts the row or overwrites every column of the existing one (projections replay events at least once). */
    Mono<Integer> upsert(AnnounceJobDetail record);
}
