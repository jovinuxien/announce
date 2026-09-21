package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallFootprint;
import org.linlinjava.announce.db.domain.LitemallFootprintExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_footprint}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallFootprintExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallFootprintMapper.xml}.
 */
public interface LitemallFootprintMapper {
    Mono<Long> countByExample(LitemallFootprintExample example);
    Mono<Integer> deleteByExample(LitemallFootprintExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallFootprint record);
    Mono<Integer> insertSelective(LitemallFootprint record);
    Mono<LitemallFootprint> selectOneByExample(LitemallFootprintExample example);
    Mono<LitemallFootprint> selectOneByExampleSelective(@Param("example") LitemallFootprintExample example, @Param("selective") LitemallFootprint.Column ... selective);
    Flux<LitemallFootprint> selectByExampleSelective(@Param("example") LitemallFootprintExample example, @Param("selective") LitemallFootprint.Column ... selective);
    Flux<LitemallFootprint> selectByExample(LitemallFootprintExample example);
    Mono<LitemallFootprint> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallFootprint.Column ... selective);
    Mono<LitemallFootprint> selectByPrimaryKey(Integer id);
    Mono<LitemallFootprint> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallFootprint record, @Param("example") LitemallFootprintExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallFootprint record, @Param("example") LitemallFootprintExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallFootprint record);
    Mono<Integer> updateByPrimaryKey(LitemallFootprint record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallFootprintExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallFootprint> selectPageByExample(@Param("example") LitemallFootprintExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallFootprint> selectPageByExampleSelective(@Param("example") LitemallFootprintExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallFootprint.Column... selective);
}
