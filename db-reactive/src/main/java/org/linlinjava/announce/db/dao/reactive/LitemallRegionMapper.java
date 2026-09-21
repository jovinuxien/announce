package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallRegion;
import org.linlinjava.announce.db.domain.LitemallRegionExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_region}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallRegionExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallRegionMapper.xml}.
 */
public interface LitemallRegionMapper {
    Mono<Long> countByExample(LitemallRegionExample example);
    Mono<Integer> deleteByExample(LitemallRegionExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallRegion record);
    Mono<Integer> insertSelective(LitemallRegion record);
    Mono<LitemallRegion> selectOneByExample(LitemallRegionExample example);
    Mono<LitemallRegion> selectOneByExampleSelective(@Param("example") LitemallRegionExample example, @Param("selective") LitemallRegion.Column ... selective);
    Flux<LitemallRegion> selectByExampleSelective(@Param("example") LitemallRegionExample example, @Param("selective") LitemallRegion.Column ... selective);
    Flux<LitemallRegion> selectByExample(LitemallRegionExample example);
    Mono<LitemallRegion> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallRegion.Column ... selective);
    Mono<LitemallRegion> selectByPrimaryKey(Integer id);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallRegion record, @Param("example") LitemallRegionExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallRegion record, @Param("example") LitemallRegionExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallRegion record);
    Mono<Integer> updateByPrimaryKey(LitemallRegion record);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallRegion> selectPageByExample(@Param("example") LitemallRegionExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallRegion> selectPageByExampleSelective(@Param("example") LitemallRegionExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallRegion.Column... selective);
}
