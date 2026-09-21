package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallLog;
import org.linlinjava.announce.db.domain.LitemallLogExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_log}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallLogExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallLogMapper.xml}.
 */
public interface LitemallLogMapper {
    Mono<Long> countByExample(LitemallLogExample example);
    Mono<Integer> deleteByExample(LitemallLogExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallLog record);
    Mono<Integer> insertSelective(LitemallLog record);
    Mono<LitemallLog> selectOneByExample(LitemallLogExample example);
    Mono<LitemallLog> selectOneByExampleSelective(@Param("example") LitemallLogExample example, @Param("selective") LitemallLog.Column ... selective);
    Flux<LitemallLog> selectByExampleSelective(@Param("example") LitemallLogExample example, @Param("selective") LitemallLog.Column ... selective);
    Flux<LitemallLog> selectByExample(LitemallLogExample example);
    Mono<LitemallLog> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallLog.Column ... selective);
    Mono<LitemallLog> selectByPrimaryKey(Integer id);
    Mono<LitemallLog> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallLog record, @Param("example") LitemallLogExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallLog record, @Param("example") LitemallLogExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallLog record);
    Mono<Integer> updateByPrimaryKey(LitemallLog record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallLogExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallLog> selectPageByExample(@Param("example") LitemallLogExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallLog> selectPageByExampleSelective(@Param("example") LitemallLogExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallLog.Column... selective);
}
