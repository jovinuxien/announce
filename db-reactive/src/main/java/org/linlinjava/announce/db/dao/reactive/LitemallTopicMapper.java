package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallTopic;
import org.linlinjava.announce.db.domain.LitemallTopicExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_topic}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallTopicExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallTopicMapper.xml}.
 */
public interface LitemallTopicMapper {
    Mono<Long> countByExample(LitemallTopicExample example);
    Mono<Integer> deleteByExample(LitemallTopicExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallTopic record);
    Mono<Integer> insertSelective(LitemallTopic record);
    Mono<LitemallTopic> selectOneByExample(LitemallTopicExample example);
    Mono<LitemallTopic> selectOneByExampleSelective(@Param("example") LitemallTopicExample example, @Param("selective") LitemallTopic.Column ... selective);
    Mono<LitemallTopic> selectOneByExampleWithBLOBs(LitemallTopicExample example);
    Flux<LitemallTopic> selectByExampleSelective(@Param("example") LitemallTopicExample example, @Param("selective") LitemallTopic.Column ... selective);
    Flux<LitemallTopic> selectByExampleWithBLOBs(LitemallTopicExample example);
    Flux<LitemallTopic> selectByExample(LitemallTopicExample example);
    Mono<LitemallTopic> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallTopic.Column ... selective);
    Mono<LitemallTopic> selectByPrimaryKey(Integer id);
    Mono<LitemallTopic> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallTopic record, @Param("example") LitemallTopicExample example);
    Mono<Integer> updateByExampleWithBLOBs(@Param("record") LitemallTopic record, @Param("example") LitemallTopicExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallTopic record, @Param("example") LitemallTopicExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallTopic record);
    Mono<Integer> updateByPrimaryKeyWithBLOBs(LitemallTopic record);
    Mono<Integer> updateByPrimaryKey(LitemallTopic record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallTopicExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallTopic> selectPageByExample(@Param("example") LitemallTopicExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallTopic> selectPageByExampleSelective(@Param("example") LitemallTopicExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallTopic.Column... selective);
}
