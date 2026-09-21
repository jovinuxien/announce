package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallComment;
import org.linlinjava.announce.db.domain.LitemallCommentExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_comment}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallCommentExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallCommentMapper.xml}.
 */
public interface LitemallCommentMapper {
    Mono<Long> countByExample(LitemallCommentExample example);
    Mono<Integer> deleteByExample(LitemallCommentExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallComment record);
    Mono<Integer> insertSelective(LitemallComment record);
    Mono<LitemallComment> selectOneByExample(LitemallCommentExample example);
    Mono<LitemallComment> selectOneByExampleSelective(@Param("example") LitemallCommentExample example, @Param("selective") LitemallComment.Column ... selective);
    Flux<LitemallComment> selectByExampleSelective(@Param("example") LitemallCommentExample example, @Param("selective") LitemallComment.Column ... selective);
    Flux<LitemallComment> selectByExample(LitemallCommentExample example);
    Mono<LitemallComment> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallComment.Column ... selective);
    Mono<LitemallComment> selectByPrimaryKey(Integer id);
    Mono<LitemallComment> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallComment record, @Param("example") LitemallCommentExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallComment record, @Param("example") LitemallCommentExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallComment record);
    Mono<Integer> updateByPrimaryKey(LitemallComment record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallCommentExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallComment> selectPageByExample(@Param("example") LitemallCommentExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallComment> selectPageByExampleSelective(@Param("example") LitemallCommentExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallComment.Column... selective);
}
