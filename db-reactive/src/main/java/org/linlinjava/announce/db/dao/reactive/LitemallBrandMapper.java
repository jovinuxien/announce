package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallBrand;
import org.linlinjava.announce.db.domain.LitemallBrandExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_brand}.
 *
 * <p>First mapper migrated from the blocking MyBatis generator output. The method names and the
 * {@link LitemallBrandExample} criteria API are kept so that existing call sites port mechanically;
 * only the return types change:
 * <ul>
 *   <li>single row: {@code Mono<LitemallBrand>} (empty when no row matches)</li>
 *   <li>many rows: {@code Flux<LitemallBrand>}</li>
 *   <li>counts and affected rows: {@code Mono<Long>} / {@code Mono<Integer>}</li>
 * </ul>
 *
 * <p>PageHelper is a MyBatis plugin and plugins are not supported by reactive-mybatis-support, so
 * pagination is explicit: use {@link #selectPageByExample} / {@link #selectPageByExampleSelective}.
 *
 * <p>SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallBrandMapper.xml}.
 */
public interface LitemallBrandMapper {

    Mono<Long> countByExample(LitemallBrandExample example);

    Mono<Integer> deleteByExample(LitemallBrandExample example);

    Mono<Integer> deleteByPrimaryKey(Integer id);

    /** Inserts all columns; the generated id is written back into {@code record.id}. */
    Mono<Integer> insert(LitemallBrand record);

    /** Inserts only non-null columns; the generated id is written back into {@code record.id}. */
    Mono<Integer> insertSelective(LitemallBrand record);

    Mono<LitemallBrand> selectOneByExample(LitemallBrandExample example);

    Mono<LitemallBrand> selectOneByExampleSelective(@Param("example") LitemallBrandExample example,
                                                    @Param("selective") LitemallBrand.Column... selective);

    Flux<LitemallBrand> selectByExample(LitemallBrandExample example);

    Flux<LitemallBrand> selectByExampleSelective(@Param("example") LitemallBrandExample example,
                                                 @Param("selective") LitemallBrand.Column... selective);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallBrand> selectPageByExample(@Param("example") LitemallBrandExample example,
                                            @Param("offset") long offset,
                                            @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallBrand> selectPageByExampleSelective(@Param("example") LitemallBrandExample example,
                                                     @Param("offset") long offset,
                                                     @Param("limit") int limit,
                                                     @Param("selective") LitemallBrand.Column... selective);

    Mono<LitemallBrand> selectByPrimaryKey(Integer id);

    Mono<LitemallBrand> selectByPrimaryKeySelective(@Param("id") Integer id,
                                                    @Param("selective") LitemallBrand.Column... selective);

    Mono<LitemallBrand> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id,
                                                            @Param("andLogicalDeleted") boolean andLogicalDeleted);

    Mono<Integer> updateByExampleSelective(@Param("record") LitemallBrand record,
                                           @Param("example") LitemallBrandExample example);

    Mono<Integer> updateByExample(@Param("record") LitemallBrand record,
                                  @Param("example") LitemallBrandExample example);

    Mono<Integer> updateByPrimaryKeySelective(LitemallBrand record);

    Mono<Integer> updateByPrimaryKey(LitemallBrand record);

    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallBrandExample example);

    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);
}
