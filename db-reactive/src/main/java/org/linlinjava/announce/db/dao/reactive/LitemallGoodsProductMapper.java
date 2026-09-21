package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallGoodsProduct;
import org.linlinjava.announce.db.domain.LitemallGoodsProductExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_goods_product}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallGoodsProductExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallGoodsProductMapper.xml}.
 */
public interface LitemallGoodsProductMapper {
    Mono<Long> countByExample(LitemallGoodsProductExample example);
    Mono<Integer> deleteByExample(LitemallGoodsProductExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallGoodsProduct record);
    Mono<Integer> insertSelective(LitemallGoodsProduct record);
    Mono<LitemallGoodsProduct> selectOneByExample(LitemallGoodsProductExample example);
    Mono<LitemallGoodsProduct> selectOneByExampleSelective(@Param("example") LitemallGoodsProductExample example, @Param("selective") LitemallGoodsProduct.Column ... selective);
    Flux<LitemallGoodsProduct> selectByExampleSelective(@Param("example") LitemallGoodsProductExample example, @Param("selective") LitemallGoodsProduct.Column ... selective);
    Flux<LitemallGoodsProduct> selectByExample(LitemallGoodsProductExample example);
    Mono<LitemallGoodsProduct> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallGoodsProduct.Column ... selective);
    Mono<LitemallGoodsProduct> selectByPrimaryKey(Integer id);
    Mono<LitemallGoodsProduct> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallGoodsProduct record, @Param("example") LitemallGoodsProductExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallGoodsProduct record, @Param("example") LitemallGoodsProductExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallGoodsProduct record);
    Mono<Integer> updateByPrimaryKey(LitemallGoodsProduct record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallGoodsProductExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallGoodsProduct> selectPageByExample(@Param("example") LitemallGoodsProductExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallGoodsProduct> selectPageByExampleSelective(@Param("example") LitemallGoodsProductExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallGoodsProduct.Column... selective);
}
