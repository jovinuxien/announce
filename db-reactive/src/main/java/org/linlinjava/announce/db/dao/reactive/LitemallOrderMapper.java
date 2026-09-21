package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallOrder;
import org.linlinjava.announce.db.domain.LitemallOrderExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_order}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallOrderExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallOrderMapper.xml}.
 */
public interface LitemallOrderMapper {
    Mono<Long> countByExample(LitemallOrderExample example);
    Mono<Integer> deleteByExample(LitemallOrderExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallOrder record);
    Mono<Integer> insertSelective(LitemallOrder record);
    Mono<LitemallOrder> selectOneByExample(LitemallOrderExample example);
    Mono<LitemallOrder> selectOneByExampleSelective(@Param("example") LitemallOrderExample example, @Param("selective") LitemallOrder.Column ... selective);
    Flux<LitemallOrder> selectByExampleSelective(@Param("example") LitemallOrderExample example, @Param("selective") LitemallOrder.Column ... selective);
    Flux<LitemallOrder> selectByExample(LitemallOrderExample example);
    Mono<LitemallOrder> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallOrder.Column ... selective);
    Mono<LitemallOrder> selectByPrimaryKey(Integer id);
    Mono<LitemallOrder> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallOrder record, @Param("example") LitemallOrderExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallOrder record, @Param("example") LitemallOrderExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallOrder record);
    Mono<Integer> updateByPrimaryKey(LitemallOrder record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallOrderExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallOrder> selectPageByExample(@Param("example") LitemallOrderExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallOrder> selectPageByExampleSelective(@Param("example") LitemallOrderExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallOrder.Column... selective);
}
