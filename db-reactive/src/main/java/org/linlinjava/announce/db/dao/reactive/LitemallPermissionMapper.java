package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallPermission;
import org.linlinjava.announce.db.domain.LitemallPermissionExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_permission}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallPermissionExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallPermissionMapper.xml}.
 */
public interface LitemallPermissionMapper {
    Mono<Long> countByExample(LitemallPermissionExample example);
    Mono<Integer> deleteByExample(LitemallPermissionExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallPermission record);
    Mono<Integer> insertSelective(LitemallPermission record);
    Mono<LitemallPermission> selectOneByExample(LitemallPermissionExample example);
    Mono<LitemallPermission> selectOneByExampleSelective(@Param("example") LitemallPermissionExample example, @Param("selective") LitemallPermission.Column ... selective);
    Flux<LitemallPermission> selectByExampleSelective(@Param("example") LitemallPermissionExample example, @Param("selective") LitemallPermission.Column ... selective);
    Flux<LitemallPermission> selectByExample(LitemallPermissionExample example);
    Mono<LitemallPermission> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallPermission.Column ... selective);
    Mono<LitemallPermission> selectByPrimaryKey(Integer id);
    Mono<LitemallPermission> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallPermission record, @Param("example") LitemallPermissionExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallPermission record, @Param("example") LitemallPermissionExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallPermission record);
    Mono<Integer> updateByPrimaryKey(LitemallPermission record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallPermissionExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallPermission> selectPageByExample(@Param("example") LitemallPermissionExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallPermission> selectPageByExampleSelective(@Param("example") LitemallPermissionExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallPermission.Column... selective);
}
