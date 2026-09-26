package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.AnnounceOrderSeller;
import org.linlinjava.announce.db.domain.AnnounceOrderSellerExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_order_seller}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link AnnounceOrderSellerExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/AnnounceOrderSellerMapper.xml}.
 */
public interface AnnounceOrderSellerMapper {
    Mono<Long> countByExample(AnnounceOrderSellerExample example);
    Mono<Integer> deleteByExample(AnnounceOrderSellerExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(AnnounceOrderSeller record);
    Mono<Integer> insertSelective(AnnounceOrderSeller record);
    Mono<AnnounceOrderSeller> selectOneByExample(AnnounceOrderSellerExample example);
    Mono<AnnounceOrderSeller> selectOneByExampleSelective(@Param("example") AnnounceOrderSellerExample example, @Param("selective") AnnounceOrderSeller.Column ... selective);
    Flux<AnnounceOrderSeller> selectByExampleSelective(@Param("example") AnnounceOrderSellerExample example, @Param("selective") AnnounceOrderSeller.Column ... selective);
    Flux<AnnounceOrderSeller> selectByExample(AnnounceOrderSellerExample example);
    Mono<AnnounceOrderSeller> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") AnnounceOrderSeller.Column ... selective);
    Mono<AnnounceOrderSeller> selectByPrimaryKey(Integer id);
    Mono<Integer> updateByExampleSelective(@Param("record") AnnounceOrderSeller record, @Param("example") AnnounceOrderSellerExample example);
    Mono<Integer> updateByExample(@Param("record") AnnounceOrderSeller record, @Param("example") AnnounceOrderSellerExample example);
    Mono<Integer> updateByPrimaryKeySelective(AnnounceOrderSeller record);
    Mono<Integer> updateByPrimaryKey(AnnounceOrderSeller record);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceOrderSeller> selectPageByExample(@Param("example") AnnounceOrderSellerExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceOrderSeller> selectPageByExampleSelective(@Param("example") AnnounceOrderSellerExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") AnnounceOrderSeller.Column... selective);
}
