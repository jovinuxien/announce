package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.AnnounceOrderSnapshot;
import org.linlinjava.announce.db.domain.AnnounceOrderSnapshotExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_order_snapshot}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link AnnounceOrderSnapshotExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/AnnounceOrderSnapshotMapper.xml}.
 */
public interface AnnounceOrderSnapshotMapper {
    Mono<Long> countByExample(AnnounceOrderSnapshotExample example);
    Mono<Integer> deleteByExample(AnnounceOrderSnapshotExample example);
    Mono<Integer> deleteByPrimaryKey(String id);
    Mono<Integer> insert(AnnounceOrderSnapshot record);
    Mono<Integer> insertSelective(AnnounceOrderSnapshot record);
    Mono<AnnounceOrderSnapshot> selectOneByExample(AnnounceOrderSnapshotExample example);
    Mono<AnnounceOrderSnapshot> selectOneByExampleSelective(@Param("example") AnnounceOrderSnapshotExample example, @Param("selective") AnnounceOrderSnapshot.Column ... selective);
    Flux<AnnounceOrderSnapshot> selectByExampleSelective(@Param("example") AnnounceOrderSnapshotExample example, @Param("selective") AnnounceOrderSnapshot.Column ... selective);
    Flux<AnnounceOrderSnapshot> selectByExample(AnnounceOrderSnapshotExample example);
    Mono<AnnounceOrderSnapshot> selectByPrimaryKeySelective(@Param("id") String id, @Param("selective") AnnounceOrderSnapshot.Column ... selective);
    Mono<AnnounceOrderSnapshot> selectByPrimaryKey(String id);
    Mono<Integer> updateByExampleSelective(@Param("record") AnnounceOrderSnapshot record, @Param("example") AnnounceOrderSnapshotExample example);
    Mono<Integer> updateByExample(@Param("record") AnnounceOrderSnapshot record, @Param("example") AnnounceOrderSnapshotExample example);
    Mono<Integer> updateByPrimaryKeySelective(AnnounceOrderSnapshot record);
    Mono<Integer> updateByPrimaryKey(AnnounceOrderSnapshot record);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceOrderSnapshot> selectPageByExample(@Param("example") AnnounceOrderSnapshotExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceOrderSnapshot> selectPageByExampleSelective(@Param("example") AnnounceOrderSnapshotExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") AnnounceOrderSnapshot.Column... selective);
}
