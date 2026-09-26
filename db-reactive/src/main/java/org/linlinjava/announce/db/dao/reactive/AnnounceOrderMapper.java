package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.AnnounceOrder;
import org.linlinjava.announce.db.domain.AnnounceOrderExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_order}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link AnnounceOrderExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/AnnounceOrderMapper.xml}.
 */
public interface AnnounceOrderMapper {
    Mono<Long> countByExample(AnnounceOrderExample example);
    Mono<Integer> deleteByExample(AnnounceOrderExample example);
    Mono<Integer> deleteByPrimaryKey(String id);
    Mono<Integer> insert(AnnounceOrder record);
    Mono<Integer> insertSelective(AnnounceOrder record);
    Mono<AnnounceOrder> selectOneByExample(AnnounceOrderExample example);
    Mono<AnnounceOrder> selectOneByExampleSelective(@Param("example") AnnounceOrderExample example, @Param("selective") AnnounceOrder.Column ... selective);
    Flux<AnnounceOrder> selectByExampleSelective(@Param("example") AnnounceOrderExample example, @Param("selective") AnnounceOrder.Column ... selective);
    Flux<AnnounceOrder> selectByExample(AnnounceOrderExample example);
    Mono<AnnounceOrder> selectByPrimaryKeySelective(@Param("id") String id, @Param("selective") AnnounceOrder.Column ... selective);
    Mono<AnnounceOrder> selectByPrimaryKey(String id);
    Mono<AnnounceOrder> selectByPrimaryKeyWithLogicalDelete(@Param("id") String id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") AnnounceOrder record, @Param("example") AnnounceOrderExample example);
    Mono<Integer> updateByExample(@Param("record") AnnounceOrder record, @Param("example") AnnounceOrderExample example);
    Mono<Integer> updateByPrimaryKeySelective(AnnounceOrder record);
    Mono<Integer> updateByPrimaryKey(AnnounceOrder record);
    Mono<Integer> logicalDeleteByExample(@Param("example") AnnounceOrderExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(String id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceOrder> selectPageByExample(@Param("example") AnnounceOrderExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceOrder> selectPageByExampleSelective(@Param("example") AnnounceOrderExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") AnnounceOrder.Column... selective);
}
