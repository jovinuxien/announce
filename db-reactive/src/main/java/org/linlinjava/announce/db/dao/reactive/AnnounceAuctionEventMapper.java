package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.AnnounceAuctionEvent;
import org.linlinjava.announce.db.domain.AnnounceAuctionEventExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_auction_event}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link AnnounceAuctionEventExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/AnnounceAuctionEventMapper.xml}.
 */
public interface AnnounceAuctionEventMapper {
    Mono<Long> countByExample(AnnounceAuctionEventExample example);
    Mono<Integer> deleteByExample(AnnounceAuctionEventExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(AnnounceAuctionEvent record);
    Mono<Integer> insertSelective(AnnounceAuctionEvent record);
    Mono<AnnounceAuctionEvent> selectOneByExample(AnnounceAuctionEventExample example);
    Mono<AnnounceAuctionEvent> selectOneByExampleSelective(@Param("example") AnnounceAuctionEventExample example, @Param("selective") AnnounceAuctionEvent.Column ... selective);
    Mono<AnnounceAuctionEvent> selectOneByExampleWithBLOBs(AnnounceAuctionEventExample example);
    Flux<AnnounceAuctionEvent> selectByExampleSelective(@Param("example") AnnounceAuctionEventExample example, @Param("selective") AnnounceAuctionEvent.Column ... selective);
    Flux<AnnounceAuctionEvent> selectByExampleWithBLOBs(AnnounceAuctionEventExample example);
    Flux<AnnounceAuctionEvent> selectByExample(AnnounceAuctionEventExample example);
    Mono<AnnounceAuctionEvent> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") AnnounceAuctionEvent.Column ... selective);
    Mono<AnnounceAuctionEvent> selectByPrimaryKey(Integer id);
    Mono<Integer> updateByExampleSelective(@Param("record") AnnounceAuctionEvent record, @Param("example") AnnounceAuctionEventExample example);
    Mono<Integer> updateByExampleWithBLOBs(@Param("record") AnnounceAuctionEvent record, @Param("example") AnnounceAuctionEventExample example);
    Mono<Integer> updateByExample(@Param("record") AnnounceAuctionEvent record, @Param("example") AnnounceAuctionEventExample example);
    Mono<Integer> updateByPrimaryKeySelective(AnnounceAuctionEvent record);
    Mono<Integer> updateByPrimaryKeyWithBLOBs(AnnounceAuctionEvent record);
    Mono<Integer> updateByPrimaryKey(AnnounceAuctionEvent record);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceAuctionEvent> selectPageByExample(@Param("example") AnnounceAuctionEventExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceAuctionEvent> selectPageByExampleSelective(@Param("example") AnnounceAuctionEventExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") AnnounceAuctionEvent.Column... selective);
}
