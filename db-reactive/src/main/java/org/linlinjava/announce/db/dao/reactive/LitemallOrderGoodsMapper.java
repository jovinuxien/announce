package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallOrderGoods;
import org.linlinjava.announce.db.domain.LitemallOrderGoodsExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_order_goods}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallOrderGoodsExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallOrderGoodsMapper.xml}.
 */
public interface LitemallOrderGoodsMapper {
    Mono<Long> countByExample(LitemallOrderGoodsExample example);
    Mono<Integer> deleteByExample(LitemallOrderGoodsExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallOrderGoods record);
    Mono<Integer> insertSelective(LitemallOrderGoods record);
    Mono<LitemallOrderGoods> selectOneByExample(LitemallOrderGoodsExample example);
    Mono<LitemallOrderGoods> selectOneByExampleSelective(@Param("example") LitemallOrderGoodsExample example, @Param("selective") LitemallOrderGoods.Column ... selective);
    Flux<LitemallOrderGoods> selectByExampleSelective(@Param("example") LitemallOrderGoodsExample example, @Param("selective") LitemallOrderGoods.Column ... selective);
    Flux<LitemallOrderGoods> selectByExample(LitemallOrderGoodsExample example);
    Mono<LitemallOrderGoods> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallOrderGoods.Column ... selective);
    Mono<LitemallOrderGoods> selectByPrimaryKey(Integer id);
    Mono<LitemallOrderGoods> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallOrderGoods record, @Param("example") LitemallOrderGoodsExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallOrderGoods record, @Param("example") LitemallOrderGoodsExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallOrderGoods record);
    Mono<Integer> updateByPrimaryKey(LitemallOrderGoods record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallOrderGoodsExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallOrderGoods> selectPageByExample(@Param("example") LitemallOrderGoodsExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallOrderGoods> selectPageByExampleSelective(@Param("example") LitemallOrderGoodsExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallOrderGoods.Column... selective);
}
