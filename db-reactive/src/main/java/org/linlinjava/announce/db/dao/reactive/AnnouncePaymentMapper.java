package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.AnnouncePayment;
import org.linlinjava.announce.db.domain.AnnouncePaymentExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_payment}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link AnnouncePaymentExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/AnnouncePaymentMapper.xml}.
 */
public interface AnnouncePaymentMapper {
    Mono<Long> countByExample(AnnouncePaymentExample example);
    Mono<Integer> deleteByExample(AnnouncePaymentExample example);
    Mono<Integer> deleteByPrimaryKey(String id);
    Mono<Integer> insert(AnnouncePayment record);
    Mono<Integer> insertSelective(AnnouncePayment record);
    Mono<AnnouncePayment> selectOneByExample(AnnouncePaymentExample example);
    Mono<AnnouncePayment> selectOneByExampleSelective(@Param("example") AnnouncePaymentExample example, @Param("selective") AnnouncePayment.Column ... selective);
    Flux<AnnouncePayment> selectByExampleSelective(@Param("example") AnnouncePaymentExample example, @Param("selective") AnnouncePayment.Column ... selective);
    Flux<AnnouncePayment> selectByExample(AnnouncePaymentExample example);
    Mono<AnnouncePayment> selectByPrimaryKeySelective(@Param("id") String id, @Param("selective") AnnouncePayment.Column ... selective);
    Mono<AnnouncePayment> selectByPrimaryKey(String id);
    Mono<Integer> updateByExampleSelective(@Param("record") AnnouncePayment record, @Param("example") AnnouncePaymentExample example);
    Mono<Integer> updateByExample(@Param("record") AnnouncePayment record, @Param("example") AnnouncePaymentExample example);
    Mono<Integer> updateByPrimaryKeySelective(AnnouncePayment record);
    Mono<Integer> updateByPrimaryKey(AnnouncePayment record);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnouncePayment> selectPageByExample(@Param("example") AnnouncePaymentExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnouncePayment> selectPageByExampleSelective(@Param("example") AnnouncePaymentExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") AnnouncePayment.Column... selective);
}
