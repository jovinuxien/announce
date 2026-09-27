package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.AnnounceShipment;
import org.linlinjava.announce.db.domain.AnnounceShipmentExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_shipment}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link AnnounceShipmentExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/AnnounceShipmentMapper.xml}.
 */
public interface AnnounceShipmentMapper {
    Mono<Long> countByExample(AnnounceShipmentExample example);
    Mono<Integer> deleteByExample(AnnounceShipmentExample example);
    Mono<Integer> deleteByPrimaryKey(String id);
    Mono<Integer> insert(AnnounceShipment record);
    Mono<Integer> insertSelective(AnnounceShipment record);
    Mono<AnnounceShipment> selectOneByExample(AnnounceShipmentExample example);
    Mono<AnnounceShipment> selectOneByExampleSelective(@Param("example") AnnounceShipmentExample example, @Param("selective") AnnounceShipment.Column ... selective);
    Flux<AnnounceShipment> selectByExampleSelective(@Param("example") AnnounceShipmentExample example, @Param("selective") AnnounceShipment.Column ... selective);
    Flux<AnnounceShipment> selectByExample(AnnounceShipmentExample example);
    Mono<AnnounceShipment> selectByPrimaryKeySelective(@Param("id") String id, @Param("selective") AnnounceShipment.Column ... selective);
    Mono<AnnounceShipment> selectByPrimaryKey(String id);
    Mono<Integer> updateByExampleSelective(@Param("record") AnnounceShipment record, @Param("example") AnnounceShipmentExample example);
    Mono<Integer> updateByExample(@Param("record") AnnounceShipment record, @Param("example") AnnounceShipmentExample example);
    Mono<Integer> updateByPrimaryKeySelective(AnnounceShipment record);
    Mono<Integer> updateByPrimaryKey(AnnounceShipment record);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceShipment> selectPageByExample(@Param("example") AnnounceShipmentExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceShipment> selectPageByExampleSelective(@Param("example") AnnounceShipmentExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") AnnounceShipment.Column... selective);
}
