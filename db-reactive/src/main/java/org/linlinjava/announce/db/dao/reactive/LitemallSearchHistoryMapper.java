package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallSearchHistory;
import org.linlinjava.announce.db.domain.LitemallSearchHistoryExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_search_history}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallSearchHistoryExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallSearchHistoryMapper.xml}.
 */
public interface LitemallSearchHistoryMapper {
    Mono<Long> countByExample(LitemallSearchHistoryExample example);
    Mono<Integer> deleteByExample(LitemallSearchHistoryExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallSearchHistory record);
    Mono<Integer> insertSelective(LitemallSearchHistory record);
    Mono<LitemallSearchHistory> selectOneByExample(LitemallSearchHistoryExample example);
    Mono<LitemallSearchHistory> selectOneByExampleSelective(@Param("example") LitemallSearchHistoryExample example, @Param("selective") LitemallSearchHistory.Column ... selective);
    Flux<LitemallSearchHistory> selectByExampleSelective(@Param("example") LitemallSearchHistoryExample example, @Param("selective") LitemallSearchHistory.Column ... selective);
    Flux<LitemallSearchHistory> selectByExample(LitemallSearchHistoryExample example);
    Mono<LitemallSearchHistory> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallSearchHistory.Column ... selective);
    Mono<LitemallSearchHistory> selectByPrimaryKey(Integer id);
    Mono<LitemallSearchHistory> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallSearchHistory record, @Param("example") LitemallSearchHistoryExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallSearchHistory record, @Param("example") LitemallSearchHistoryExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallSearchHistory record);
    Mono<Integer> updateByPrimaryKey(LitemallSearchHistory record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallSearchHistoryExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallSearchHistory> selectPageByExample(@Param("example") LitemallSearchHistoryExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallSearchHistory> selectPageByExampleSelective(@Param("example") LitemallSearchHistoryExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallSearchHistory.Column... selective);
}
