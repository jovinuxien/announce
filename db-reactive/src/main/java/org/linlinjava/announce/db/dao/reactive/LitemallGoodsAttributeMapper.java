package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallGoodsAttribute;
import org.linlinjava.announce.db.domain.LitemallGoodsAttributeExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_goods_attribute}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallGoodsAttributeExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallGoodsAttributeMapper.xml}.
 */
public interface LitemallGoodsAttributeMapper {
    Mono<Long> countByExample(LitemallGoodsAttributeExample example);
    Mono<Integer> deleteByExample(LitemallGoodsAttributeExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallGoodsAttribute record);
    Mono<Integer> insertSelective(LitemallGoodsAttribute record);
    Mono<LitemallGoodsAttribute> selectOneByExample(LitemallGoodsAttributeExample example);
    Mono<LitemallGoodsAttribute> selectOneByExampleSelective(@Param("example") LitemallGoodsAttributeExample example, @Param("selective") LitemallGoodsAttribute.Column ... selective);
    Flux<LitemallGoodsAttribute> selectByExampleSelective(@Param("example") LitemallGoodsAttributeExample example, @Param("selective") LitemallGoodsAttribute.Column ... selective);
    Flux<LitemallGoodsAttribute> selectByExample(LitemallGoodsAttributeExample example);
    Mono<LitemallGoodsAttribute> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallGoodsAttribute.Column ... selective);
    Mono<LitemallGoodsAttribute> selectByPrimaryKey(Integer id);
    Mono<LitemallGoodsAttribute> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallGoodsAttribute record, @Param("example") LitemallGoodsAttributeExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallGoodsAttribute record, @Param("example") LitemallGoodsAttributeExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallGoodsAttribute record);
    Mono<Integer> updateByPrimaryKey(LitemallGoodsAttribute record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallGoodsAttributeExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallGoodsAttribute> selectPageByExample(@Param("example") LitemallGoodsAttributeExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallGoodsAttribute> selectPageByExampleSelective(@Param("example") LitemallGoodsAttributeExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallGoodsAttribute.Column... selective);
}
