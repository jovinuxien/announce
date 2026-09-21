package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallKeyword;
import org.linlinjava.announce.db.domain.LitemallKeywordExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_keyword}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallKeywordExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallKeywordMapper.xml}.
 */
public interface LitemallKeywordMapper {
    Mono<Long> countByExample(LitemallKeywordExample example);
    Mono<Integer> deleteByExample(LitemallKeywordExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallKeyword record);
    Mono<Integer> insertSelective(LitemallKeyword record);
    Mono<LitemallKeyword> selectOneByExample(LitemallKeywordExample example);
    Mono<LitemallKeyword> selectOneByExampleSelective(@Param("example") LitemallKeywordExample example, @Param("selective") LitemallKeyword.Column ... selective);
    Flux<LitemallKeyword> selectByExampleSelective(@Param("example") LitemallKeywordExample example, @Param("selective") LitemallKeyword.Column ... selective);
    Flux<LitemallKeyword> selectByExample(LitemallKeywordExample example);
    Mono<LitemallKeyword> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallKeyword.Column ... selective);
    Mono<LitemallKeyword> selectByPrimaryKey(Integer id);
    Mono<LitemallKeyword> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallKeyword record, @Param("example") LitemallKeywordExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallKeyword record, @Param("example") LitemallKeywordExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallKeyword record);
    Mono<Integer> updateByPrimaryKey(LitemallKeyword record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallKeywordExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallKeyword> selectPageByExample(@Param("example") LitemallKeywordExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallKeyword> selectPageByExampleSelective(@Param("example") LitemallKeywordExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallKeyword.Column... selective);
}
