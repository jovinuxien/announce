package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallNoticeAdmin;
import org.linlinjava.announce.db.domain.LitemallNoticeAdminExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_notice_admin}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallNoticeAdminExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallNoticeAdminMapper.xml}.
 */
public interface LitemallNoticeAdminMapper {
    Mono<Long> countByExample(LitemallNoticeAdminExample example);
    Mono<Integer> deleteByExample(LitemallNoticeAdminExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallNoticeAdmin record);
    Mono<Integer> insertSelective(LitemallNoticeAdmin record);
    Mono<LitemallNoticeAdmin> selectOneByExample(LitemallNoticeAdminExample example);
    Mono<LitemallNoticeAdmin> selectOneByExampleSelective(@Param("example") LitemallNoticeAdminExample example, @Param("selective") LitemallNoticeAdmin.Column ... selective);
    Flux<LitemallNoticeAdmin> selectByExampleSelective(@Param("example") LitemallNoticeAdminExample example, @Param("selective") LitemallNoticeAdmin.Column ... selective);
    Flux<LitemallNoticeAdmin> selectByExample(LitemallNoticeAdminExample example);
    Mono<LitemallNoticeAdmin> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallNoticeAdmin.Column ... selective);
    Mono<LitemallNoticeAdmin> selectByPrimaryKey(Integer id);
    Mono<LitemallNoticeAdmin> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallNoticeAdmin record, @Param("example") LitemallNoticeAdminExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallNoticeAdmin record, @Param("example") LitemallNoticeAdminExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallNoticeAdmin record);
    Mono<Integer> updateByPrimaryKey(LitemallNoticeAdmin record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallNoticeAdminExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallNoticeAdmin> selectPageByExample(@Param("example") LitemallNoticeAdminExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallNoticeAdmin> selectPageByExampleSelective(@Param("example") LitemallNoticeAdminExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallNoticeAdmin.Column... selective);
}
