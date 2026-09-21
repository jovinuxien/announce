package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallFeedback;
import org.linlinjava.announce.db.domain.LitemallFeedbackExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_feedback}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallFeedbackExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallFeedbackMapper.xml}.
 */
public interface LitemallFeedbackMapper {
    Mono<Long> countByExample(LitemallFeedbackExample example);
    Mono<Integer> deleteByExample(LitemallFeedbackExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallFeedback record);
    Mono<Integer> insertSelective(LitemallFeedback record);
    Mono<LitemallFeedback> selectOneByExample(LitemallFeedbackExample example);
    Mono<LitemallFeedback> selectOneByExampleSelective(@Param("example") LitemallFeedbackExample example, @Param("selective") LitemallFeedback.Column ... selective);
    Flux<LitemallFeedback> selectByExampleSelective(@Param("example") LitemallFeedbackExample example, @Param("selective") LitemallFeedback.Column ... selective);
    Flux<LitemallFeedback> selectByExample(LitemallFeedbackExample example);
    Mono<LitemallFeedback> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallFeedback.Column ... selective);
    Mono<LitemallFeedback> selectByPrimaryKey(Integer id);
    Mono<LitemallFeedback> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallFeedback record, @Param("example") LitemallFeedbackExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallFeedback record, @Param("example") LitemallFeedbackExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallFeedback record);
    Mono<Integer> updateByPrimaryKey(LitemallFeedback record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallFeedbackExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallFeedback> selectPageByExample(@Param("example") LitemallFeedbackExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallFeedback> selectPageByExampleSelective(@Param("example") LitemallFeedbackExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallFeedback.Column... selective);
}
