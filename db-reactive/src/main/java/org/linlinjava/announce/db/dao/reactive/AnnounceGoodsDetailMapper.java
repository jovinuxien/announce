package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.AnnounceGoodsDetail;
import org.linlinjava.announce.db.domain.AnnounceGoodsDetailExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_goods_detail}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link AnnounceGoodsDetailExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/AnnounceGoodsDetailMapper.xml}.
 */
public interface AnnounceGoodsDetailMapper {
    Mono<Long> countByExample(AnnounceGoodsDetailExample example);
    Mono<Integer> deleteByExample(AnnounceGoodsDetailExample example);
    Mono<Integer> deleteByPrimaryKey(String announceId);
    Mono<Integer> insert(AnnounceGoodsDetail record);
    Mono<Integer> insertSelective(AnnounceGoodsDetail record);
    Mono<AnnounceGoodsDetail> selectOneByExample(AnnounceGoodsDetailExample example);
    Mono<AnnounceGoodsDetail> selectOneByExampleSelective(@Param("example") AnnounceGoodsDetailExample example, @Param("selective") AnnounceGoodsDetail.Column ... selective);
    Flux<AnnounceGoodsDetail> selectByExampleSelective(@Param("example") AnnounceGoodsDetailExample example, @Param("selective") AnnounceGoodsDetail.Column ... selective);
    Flux<AnnounceGoodsDetail> selectByExample(AnnounceGoodsDetailExample example);
    Mono<AnnounceGoodsDetail> selectByPrimaryKeySelective(@Param("announceId") String announceId, @Param("selective") AnnounceGoodsDetail.Column ... selective);
    Mono<AnnounceGoodsDetail> selectByPrimaryKey(String announceId);
    Mono<Integer> updateByExampleSelective(@Param("record") AnnounceGoodsDetail record, @Param("example") AnnounceGoodsDetailExample example);
    Mono<Integer> updateByExample(@Param("record") AnnounceGoodsDetail record, @Param("example") AnnounceGoodsDetailExample example);
    Mono<Integer> updateByPrimaryKeySelective(AnnounceGoodsDetail record);
    Mono<Integer> updateByPrimaryKey(AnnounceGoodsDetail record);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceGoodsDetail> selectPageByExample(@Param("example") AnnounceGoodsDetailExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceGoodsDetail> selectPageByExampleSelective(@Param("example") AnnounceGoodsDetailExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") AnnounceGoodsDetail.Column... selective);

    /** Inserts the row or overwrites every column of the existing one (projections replay events at least once). */
    Mono<Integer> upsert(AnnounceGoodsDetail record);
}
