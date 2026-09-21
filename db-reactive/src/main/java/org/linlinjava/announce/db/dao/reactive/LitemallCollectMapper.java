package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallCollect;
import org.linlinjava.announce.db.domain.LitemallCollectExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_collect}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallCollectExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallCollectMapper.xml}.
 */
public interface LitemallCollectMapper {
    Mono<Long> countByExample(LitemallCollectExample example);
    Mono<Integer> deleteByExample(LitemallCollectExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallCollect record);
    Mono<Integer> insertSelective(LitemallCollect record);
    Mono<LitemallCollect> selectOneByExample(LitemallCollectExample example);
    Mono<LitemallCollect> selectOneByExampleSelective(@Param("example") LitemallCollectExample example, @Param("selective") LitemallCollect.Column ... selective);
    Flux<LitemallCollect> selectByExampleSelective(@Param("example") LitemallCollectExample example, @Param("selective") LitemallCollect.Column ... selective);
    Flux<LitemallCollect> selectByExample(LitemallCollectExample example);
    Mono<LitemallCollect> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallCollect.Column ... selective);
    Mono<LitemallCollect> selectByPrimaryKey(Integer id);
    Mono<LitemallCollect> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallCollect record, @Param("example") LitemallCollectExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallCollect record, @Param("example") LitemallCollectExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallCollect record);
    Mono<Integer> updateByPrimaryKey(LitemallCollect record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallCollectExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallCollect> selectPageByExample(@Param("example") LitemallCollectExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallCollect> selectPageByExampleSelective(@Param("example") LitemallCollectExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallCollect.Column... selective);
}
