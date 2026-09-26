package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.AnnounceAuctionSeller;
import org.linlinjava.announce.db.domain.AnnounceAuctionSellerExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_auction_seller}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link AnnounceAuctionSellerExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/AnnounceAuctionSellerMapper.xml}.
 */
public interface AnnounceAuctionSellerMapper {
    Mono<Long> countByExample(AnnounceAuctionSellerExample example);
    Mono<Integer> deleteByExample(AnnounceAuctionSellerExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(AnnounceAuctionSeller record);
    Mono<Integer> insertSelective(AnnounceAuctionSeller record);
    Mono<AnnounceAuctionSeller> selectOneByExample(AnnounceAuctionSellerExample example);
    Mono<AnnounceAuctionSeller> selectOneByExampleSelective(@Param("example") AnnounceAuctionSellerExample example, @Param("selective") AnnounceAuctionSeller.Column ... selective);
    Flux<AnnounceAuctionSeller> selectByExampleSelective(@Param("example") AnnounceAuctionSellerExample example, @Param("selective") AnnounceAuctionSeller.Column ... selective);
    Flux<AnnounceAuctionSeller> selectByExample(AnnounceAuctionSellerExample example);
    Mono<AnnounceAuctionSeller> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") AnnounceAuctionSeller.Column ... selective);
    Mono<AnnounceAuctionSeller> selectByPrimaryKey(Integer id);
    Mono<Integer> updateByExampleSelective(@Param("record") AnnounceAuctionSeller record, @Param("example") AnnounceAuctionSellerExample example);
    Mono<Integer> updateByExample(@Param("record") AnnounceAuctionSeller record, @Param("example") AnnounceAuctionSellerExample example);
    Mono<Integer> updateByPrimaryKeySelective(AnnounceAuctionSeller record);
    Mono<Integer> updateByPrimaryKey(AnnounceAuctionSeller record);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceAuctionSeller> selectPageByExample(@Param("example") AnnounceAuctionSellerExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceAuctionSeller> selectPageByExampleSelective(@Param("example") AnnounceAuctionSellerExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") AnnounceAuctionSeller.Column... selective);
}
