package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallRole;
import org.linlinjava.announce.db.domain.LitemallRoleExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_role}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallRoleExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallRoleMapper.xml}.
 */
public interface LitemallRoleMapper {
    Mono<Long> countByExample(LitemallRoleExample example);
    Mono<Integer> deleteByExample(LitemallRoleExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallRole record);
    Mono<Integer> insertSelective(LitemallRole record);
    Mono<LitemallRole> selectOneByExample(LitemallRoleExample example);
    Mono<LitemallRole> selectOneByExampleSelective(@Param("example") LitemallRoleExample example, @Param("selective") LitemallRole.Column ... selective);
    Flux<LitemallRole> selectByExampleSelective(@Param("example") LitemallRoleExample example, @Param("selective") LitemallRole.Column ... selective);
    Flux<LitemallRole> selectByExample(LitemallRoleExample example);
    Mono<LitemallRole> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallRole.Column ... selective);
    Mono<LitemallRole> selectByPrimaryKey(Integer id);
    Mono<LitemallRole> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallRole record, @Param("example") LitemallRoleExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallRole record, @Param("example") LitemallRoleExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallRole record);
    Mono<Integer> updateByPrimaryKey(LitemallRole record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallRoleExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallRole> selectPageByExample(@Param("example") LitemallRoleExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallRole> selectPageByExampleSelective(@Param("example") LitemallRoleExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallRole.Column... selective);
}
