package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.AnnounceSellerAccount;
import org.linlinjava.announce.db.domain.AnnounceSellerAccountExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_seller_account}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link AnnounceSellerAccountExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/AnnounceSellerAccountMapper.xml}.
 */
public interface AnnounceSellerAccountMapper {
    Mono<Long> countByExample(AnnounceSellerAccountExample example);
    Mono<Integer> deleteByExample(AnnounceSellerAccountExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(AnnounceSellerAccount record);
    Mono<Integer> insertSelective(AnnounceSellerAccount record);
    Mono<AnnounceSellerAccount> selectOneByExample(AnnounceSellerAccountExample example);
    Mono<AnnounceSellerAccount> selectOneByExampleSelective(@Param("example") AnnounceSellerAccountExample example, @Param("selective") AnnounceSellerAccount.Column ... selective);
    Flux<AnnounceSellerAccount> selectByExampleSelective(@Param("example") AnnounceSellerAccountExample example, @Param("selective") AnnounceSellerAccount.Column ... selective);
    Flux<AnnounceSellerAccount> selectByExample(AnnounceSellerAccountExample example);
    Mono<AnnounceSellerAccount> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") AnnounceSellerAccount.Column ... selective);
    Mono<AnnounceSellerAccount> selectByPrimaryKey(Integer id);
    Mono<Integer> updateByExampleSelective(@Param("record") AnnounceSellerAccount record, @Param("example") AnnounceSellerAccountExample example);
    Mono<Integer> updateByExample(@Param("record") AnnounceSellerAccount record, @Param("example") AnnounceSellerAccountExample example);
    Mono<Integer> updateByPrimaryKeySelective(AnnounceSellerAccount record);
    Mono<Integer> updateByPrimaryKey(AnnounceSellerAccount record);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceSellerAccount> selectPageByExample(@Param("example") AnnounceSellerAccountExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceSellerAccount> selectPageByExampleSelective(@Param("example") AnnounceSellerAccountExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") AnnounceSellerAccount.Column... selective);
}
