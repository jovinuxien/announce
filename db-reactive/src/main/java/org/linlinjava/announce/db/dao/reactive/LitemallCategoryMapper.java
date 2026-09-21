package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallCategory;
import org.linlinjava.announce.db.domain.LitemallCategoryExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_category}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallCategoryExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallCategoryMapper.xml}.
 */
public interface LitemallCategoryMapper {
    Mono<Long> countByExample(LitemallCategoryExample example);
    Mono<Integer> deleteByExample(LitemallCategoryExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallCategory record);
    Mono<Integer> insertSelective(LitemallCategory record);
    Mono<LitemallCategory> selectOneByExample(LitemallCategoryExample example);
    Mono<LitemallCategory> selectOneByExampleSelective(@Param("example") LitemallCategoryExample example, @Param("selective") LitemallCategory.Column ... selective);
    Flux<LitemallCategory> selectByExampleSelective(@Param("example") LitemallCategoryExample example, @Param("selective") LitemallCategory.Column ... selective);
    Flux<LitemallCategory> selectByExample(LitemallCategoryExample example);
    Mono<LitemallCategory> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallCategory.Column ... selective);
    Mono<LitemallCategory> selectByPrimaryKey(Integer id);
    Mono<LitemallCategory> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallCategory record, @Param("example") LitemallCategoryExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallCategory record, @Param("example") LitemallCategoryExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallCategory record);
    Mono<Integer> updateByPrimaryKey(LitemallCategory record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallCategoryExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallCategory> selectPageByExample(@Param("example") LitemallCategoryExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallCategory> selectPageByExampleSelective(@Param("example") LitemallCategoryExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallCategory.Column... selective);
}
