package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallGoods;
import org.linlinjava.announce.db.domain.LitemallGoodsExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_goods}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallGoodsExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallGoodsMapper.xml}.
 */
public interface LitemallGoodsMapper {
    Mono<Long> countByExample(LitemallGoodsExample example);
    Mono<Integer> deleteByExample(LitemallGoodsExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallGoods record);
    Mono<Integer> insertSelective(LitemallGoods record);
    Mono<LitemallGoods> selectOneByExample(LitemallGoodsExample example);
    Mono<LitemallGoods> selectOneByExampleSelective(@Param("example") LitemallGoodsExample example, @Param("selective") LitemallGoods.Column ... selective);
    Mono<LitemallGoods> selectOneByExampleWithBLOBs(LitemallGoodsExample example);
    Flux<LitemallGoods> selectByExampleSelective(@Param("example") LitemallGoodsExample example, @Param("selective") LitemallGoods.Column ... selective);
    Flux<LitemallGoods> selectByExampleWithBLOBs(LitemallGoodsExample example);
    Flux<LitemallGoods> selectByExample(LitemallGoodsExample example);
    Mono<LitemallGoods> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallGoods.Column ... selective);
    Mono<LitemallGoods> selectByPrimaryKey(Integer id);
    Mono<LitemallGoods> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallGoods record, @Param("example") LitemallGoodsExample example);
    Mono<Integer> updateByExampleWithBLOBs(@Param("record") LitemallGoods record, @Param("example") LitemallGoodsExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallGoods record, @Param("example") LitemallGoodsExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallGoods record);
    Mono<Integer> updateByPrimaryKeyWithBLOBs(LitemallGoods record);
    Mono<Integer> updateByPrimaryKey(LitemallGoods record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallGoodsExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallGoods> selectPageByExample(@Param("example") LitemallGoodsExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallGoods> selectPageByExampleSelective(@Param("example") LitemallGoodsExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallGoods.Column... selective);

    /**
     * Rewrites exactly the columns the Announce aggregate owns (nulls included): content, category, brand, sale type,
     * prices, status, expiry and location. Marketing flags, goods_sn, seller_id, add_time and deleted are left as they are.
     */
    Mono<Integer> updateAnnounce(LitemallGoods record);
}
