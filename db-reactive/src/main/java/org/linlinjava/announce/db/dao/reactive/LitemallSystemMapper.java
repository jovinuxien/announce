package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallSystem;
import org.linlinjava.announce.db.domain.LitemallSystemExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_system}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallSystemExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallSystemMapper.xml}.
 */
public interface LitemallSystemMapper {
    Mono<Long> countByExample(LitemallSystemExample example);
    Mono<Integer> deleteByExample(LitemallSystemExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallSystem record);
    Mono<Integer> insertSelective(LitemallSystem record);
    Mono<LitemallSystem> selectOneByExample(LitemallSystemExample example);
    Mono<LitemallSystem> selectOneByExampleSelective(@Param("example") LitemallSystemExample example, @Param("selective") LitemallSystem.Column ... selective);
    Flux<LitemallSystem> selectByExampleSelective(@Param("example") LitemallSystemExample example, @Param("selective") LitemallSystem.Column ... selective);
    Flux<LitemallSystem> selectByExample(LitemallSystemExample example);
    Mono<LitemallSystem> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallSystem.Column ... selective);
    Mono<LitemallSystem> selectByPrimaryKey(Integer id);
    Mono<LitemallSystem> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallSystem record, @Param("example") LitemallSystemExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallSystem record, @Param("example") LitemallSystemExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallSystem record);
    Mono<Integer> updateByPrimaryKey(LitemallSystem record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallSystemExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallSystem> selectPageByExample(@Param("example") LitemallSystemExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallSystem> selectPageByExampleSelective(@Param("example") LitemallSystemExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallSystem.Column... selective);
}
