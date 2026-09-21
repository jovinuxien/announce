package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallAd;
import org.linlinjava.announce.db.domain.LitemallAdExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_ad}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallAdExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallAdMapper.xml}.
 */
public interface LitemallAdMapper {
    Mono<Long> countByExample(LitemallAdExample example);
    Mono<Integer> deleteByExample(LitemallAdExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallAd record);
    Mono<Integer> insertSelective(LitemallAd record);
    Mono<LitemallAd> selectOneByExample(LitemallAdExample example);
    Mono<LitemallAd> selectOneByExampleSelective(@Param("example") LitemallAdExample example, @Param("selective") LitemallAd.Column ... selective);
    Flux<LitemallAd> selectByExampleSelective(@Param("example") LitemallAdExample example, @Param("selective") LitemallAd.Column ... selective);
    Flux<LitemallAd> selectByExample(LitemallAdExample example);
    Mono<LitemallAd> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallAd.Column ... selective);
    Mono<LitemallAd> selectByPrimaryKey(Integer id);
    Mono<LitemallAd> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallAd record, @Param("example") LitemallAdExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallAd record, @Param("example") LitemallAdExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallAd record);
    Mono<Integer> updateByPrimaryKey(LitemallAd record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallAdExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallAd> selectPageByExample(@Param("example") LitemallAdExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallAd> selectPageByExampleSelective(@Param("example") LitemallAdExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallAd.Column... selective);
}
