package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallStorage;
import org.linlinjava.announce.db.domain.LitemallStorageExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_storage}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallStorageExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallStorageMapper.xml}.
 */
public interface LitemallStorageMapper {
    Mono<Long> countByExample(LitemallStorageExample example);
    Mono<Integer> deleteByExample(LitemallStorageExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallStorage record);
    Mono<Integer> insertSelective(LitemallStorage record);
    Mono<LitemallStorage> selectOneByExample(LitemallStorageExample example);
    Mono<LitemallStorage> selectOneByExampleSelective(@Param("example") LitemallStorageExample example, @Param("selective") LitemallStorage.Column ... selective);
    Flux<LitemallStorage> selectByExampleSelective(@Param("example") LitemallStorageExample example, @Param("selective") LitemallStorage.Column ... selective);
    Flux<LitemallStorage> selectByExample(LitemallStorageExample example);
    Mono<LitemallStorage> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallStorage.Column ... selective);
    Mono<LitemallStorage> selectByPrimaryKey(Integer id);
    Mono<LitemallStorage> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallStorage record, @Param("example") LitemallStorageExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallStorage record, @Param("example") LitemallStorageExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallStorage record);
    Mono<Integer> updateByPrimaryKey(LitemallStorage record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallStorageExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallStorage> selectPageByExample(@Param("example") LitemallStorageExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallStorage> selectPageByExampleSelective(@Param("example") LitemallStorageExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallStorage.Column... selective);
}
