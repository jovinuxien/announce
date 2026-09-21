package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallGoodsSpecification;
import org.linlinjava.announce.db.domain.LitemallGoodsSpecificationExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_goods_specification}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallGoodsSpecificationExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallGoodsSpecificationMapper.xml}.
 */
public interface LitemallGoodsSpecificationMapper {
    Mono<Long> countByExample(LitemallGoodsSpecificationExample example);
    Mono<Integer> deleteByExample(LitemallGoodsSpecificationExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallGoodsSpecification record);
    Mono<Integer> insertSelective(LitemallGoodsSpecification record);
    Mono<LitemallGoodsSpecification> selectOneByExample(LitemallGoodsSpecificationExample example);
    Mono<LitemallGoodsSpecification> selectOneByExampleSelective(@Param("example") LitemallGoodsSpecificationExample example, @Param("selective") LitemallGoodsSpecification.Column ... selective);
    Flux<LitemallGoodsSpecification> selectByExampleSelective(@Param("example") LitemallGoodsSpecificationExample example, @Param("selective") LitemallGoodsSpecification.Column ... selective);
    Flux<LitemallGoodsSpecification> selectByExample(LitemallGoodsSpecificationExample example);
    Mono<LitemallGoodsSpecification> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallGoodsSpecification.Column ... selective);
    Mono<LitemallGoodsSpecification> selectByPrimaryKey(Integer id);
    Mono<LitemallGoodsSpecification> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallGoodsSpecification record, @Param("example") LitemallGoodsSpecificationExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallGoodsSpecification record, @Param("example") LitemallGoodsSpecificationExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallGoodsSpecification record);
    Mono<Integer> updateByPrimaryKey(LitemallGoodsSpecification record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallGoodsSpecificationExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallGoodsSpecification> selectPageByExample(@Param("example") LitemallGoodsSpecificationExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallGoodsSpecification> selectPageByExampleSelective(@Param("example") LitemallGoodsSpecificationExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallGoodsSpecification.Column... selective);
}
