package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallUser;
import org.linlinjava.announce.db.domain.LitemallUserExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_user}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallUserExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallUserMapper.xml}.
 */
public interface LitemallUserMapper {
    Mono<Long> countByExample(LitemallUserExample example);
    Mono<Integer> deleteByExample(LitemallUserExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallUser record);
    Mono<Integer> insertSelective(LitemallUser record);
    Mono<LitemallUser> selectOneByExample(LitemallUserExample example);
    Mono<LitemallUser> selectOneByExampleSelective(@Param("example") LitemallUserExample example, @Param("selective") LitemallUser.Column ... selective);
    Flux<LitemallUser> selectByExampleSelective(@Param("example") LitemallUserExample example, @Param("selective") LitemallUser.Column ... selective);
    Flux<LitemallUser> selectByExample(LitemallUserExample example);
    Mono<LitemallUser> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallUser.Column ... selective);
    Mono<LitemallUser> selectByPrimaryKey(Integer id);
    Mono<LitemallUser> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallUser record, @Param("example") LitemallUserExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallUser record, @Param("example") LitemallUserExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallUser record);
    Mono<Integer> updateByPrimaryKey(LitemallUser record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallUserExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallUser> selectPageByExample(@Param("example") LitemallUserExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallUser> selectPageByExampleSelective(@Param("example") LitemallUserExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallUser.Column... selective);
}
