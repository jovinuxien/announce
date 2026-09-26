package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.AnnounceAuction;
import org.linlinjava.announce.db.domain.AnnounceAuctionExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_auction}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link AnnounceAuctionExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/AnnounceAuctionMapper.xml}.
 */
public interface AnnounceAuctionMapper {
    Mono<Long> countByExample(AnnounceAuctionExample example);
    Mono<Integer> deleteByExample(AnnounceAuctionExample example);
    Mono<Integer> deleteByPrimaryKey(String id);
    Mono<Integer> insert(AnnounceAuction record);
    Mono<Integer> insertSelective(AnnounceAuction record);
    Mono<AnnounceAuction> selectOneByExample(AnnounceAuctionExample example);
    Mono<AnnounceAuction> selectOneByExampleSelective(@Param("example") AnnounceAuctionExample example, @Param("selective") AnnounceAuction.Column ... selective);
    Flux<AnnounceAuction> selectByExampleSelective(@Param("example") AnnounceAuctionExample example, @Param("selective") AnnounceAuction.Column ... selective);
    Flux<AnnounceAuction> selectByExample(AnnounceAuctionExample example);
    Mono<AnnounceAuction> selectByPrimaryKeySelective(@Param("id") String id, @Param("selective") AnnounceAuction.Column ... selective);
    Mono<AnnounceAuction> selectByPrimaryKey(String id);
    Mono<AnnounceAuction> selectByPrimaryKeyWithLogicalDelete(@Param("id") String id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") AnnounceAuction record, @Param("example") AnnounceAuctionExample example);
    Mono<Integer> updateByExample(@Param("record") AnnounceAuction record, @Param("example") AnnounceAuctionExample example);
    Mono<Integer> updateByPrimaryKeySelective(AnnounceAuction record);
    Mono<Integer> updateByPrimaryKey(AnnounceAuction record);
    Mono<Integer> logicalDeleteByExample(@Param("example") AnnounceAuctionExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(String id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceAuction> selectPageByExample(@Param("example") AnnounceAuctionExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceAuction> selectPageByExampleSelective(@Param("example") AnnounceAuctionExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") AnnounceAuction.Column... selective);
}
