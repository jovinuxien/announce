package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallAdmin;
import org.linlinjava.announce.db.domain.LitemallAdminExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_admin}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallAdminExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallAdminMapper.xml}.
 */
public interface LitemallAdminMapper {
    Mono<Long> countByExample(LitemallAdminExample example);
    Mono<Integer> deleteByExample(LitemallAdminExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallAdmin record);
    Mono<Integer> insertSelective(LitemallAdmin record);
    Mono<LitemallAdmin> selectOneByExample(LitemallAdminExample example);
    Mono<LitemallAdmin> selectOneByExampleSelective(@Param("example") LitemallAdminExample example, @Param("selective") LitemallAdmin.Column ... selective);
    Flux<LitemallAdmin> selectByExampleSelective(@Param("example") LitemallAdminExample example, @Param("selective") LitemallAdmin.Column ... selective);
    Flux<LitemallAdmin> selectByExample(LitemallAdminExample example);
    Mono<LitemallAdmin> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallAdmin.Column ... selective);
    Mono<LitemallAdmin> selectByPrimaryKey(Integer id);
    Mono<LitemallAdmin> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallAdmin record, @Param("example") LitemallAdminExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallAdmin record, @Param("example") LitemallAdminExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallAdmin record);
    Mono<Integer> updateByPrimaryKey(LitemallAdmin record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallAdminExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallAdmin> selectPageByExample(@Param("example") LitemallAdminExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallAdmin> selectPageByExampleSelective(@Param("example") LitemallAdminExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallAdmin.Column... selective);
}
