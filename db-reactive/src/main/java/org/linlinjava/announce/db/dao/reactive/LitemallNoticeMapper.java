package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallNotice;
import org.linlinjava.announce.db.domain.LitemallNoticeExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_notice}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallNoticeExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallNoticeMapper.xml}.
 */
public interface LitemallNoticeMapper {
    Mono<Long> countByExample(LitemallNoticeExample example);
    Mono<Integer> deleteByExample(LitemallNoticeExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallNotice record);
    Mono<Integer> insertSelective(LitemallNotice record);
    Mono<LitemallNotice> selectOneByExample(LitemallNoticeExample example);
    Mono<LitemallNotice> selectOneByExampleSelective(@Param("example") LitemallNoticeExample example, @Param("selective") LitemallNotice.Column ... selective);
    Flux<LitemallNotice> selectByExampleSelective(@Param("example") LitemallNoticeExample example, @Param("selective") LitemallNotice.Column ... selective);
    Flux<LitemallNotice> selectByExample(LitemallNoticeExample example);
    Mono<LitemallNotice> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallNotice.Column ... selective);
    Mono<LitemallNotice> selectByPrimaryKey(Integer id);
    Mono<LitemallNotice> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallNotice record, @Param("example") LitemallNoticeExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallNotice record, @Param("example") LitemallNoticeExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallNotice record);
    Mono<Integer> updateByPrimaryKey(LitemallNotice record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallNoticeExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallNotice> selectPageByExample(@Param("example") LitemallNoticeExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallNotice> selectPageByExampleSelective(@Param("example") LitemallNoticeExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallNotice.Column... selective);
}
