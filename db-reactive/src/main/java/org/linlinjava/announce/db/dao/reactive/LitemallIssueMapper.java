package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallIssue;
import org.linlinjava.announce.db.domain.LitemallIssueExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_issue}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallIssueExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallIssueMapper.xml}.
 */
public interface LitemallIssueMapper {
    Mono<Long> countByExample(LitemallIssueExample example);
    Mono<Integer> deleteByExample(LitemallIssueExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallIssue record);
    Mono<Integer> insertSelective(LitemallIssue record);
    Mono<LitemallIssue> selectOneByExample(LitemallIssueExample example);
    Mono<LitemallIssue> selectOneByExampleSelective(@Param("example") LitemallIssueExample example, @Param("selective") LitemallIssue.Column ... selective);
    Flux<LitemallIssue> selectByExampleSelective(@Param("example") LitemallIssueExample example, @Param("selective") LitemallIssue.Column ... selective);
    Flux<LitemallIssue> selectByExample(LitemallIssueExample example);
    Mono<LitemallIssue> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallIssue.Column ... selective);
    Mono<LitemallIssue> selectByPrimaryKey(Integer id);
    Mono<LitemallIssue> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallIssue record, @Param("example") LitemallIssueExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallIssue record, @Param("example") LitemallIssueExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallIssue record);
    Mono<Integer> updateByPrimaryKey(LitemallIssue record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallIssueExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallIssue> selectPageByExample(@Param("example") LitemallIssueExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallIssue> selectPageByExampleSelective(@Param("example") LitemallIssueExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallIssue.Column... selective);
}
