package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.ListingImportRun;
import org.linlinjava.announce.db.domain.ListingImportRunExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code listing_import_run}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link ListingImportRunExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/ListingImportRunMapper.xml}.
 */
public interface ListingImportRunMapper {
    Mono<Long> countByExample(ListingImportRunExample example);
    Mono<Integer> deleteByExample(ListingImportRunExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(ListingImportRun record);
    Mono<Integer> insertSelective(ListingImportRun record);
    Mono<ListingImportRun> selectOneByExample(ListingImportRunExample example);
    Mono<ListingImportRun> selectOneByExampleSelective(@Param("example") ListingImportRunExample example, @Param("selective") ListingImportRun.Column ... selective);
    Flux<ListingImportRun> selectByExampleSelective(@Param("example") ListingImportRunExample example, @Param("selective") ListingImportRun.Column ... selective);
    Flux<ListingImportRun> selectByExample(ListingImportRunExample example);
    Mono<ListingImportRun> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") ListingImportRun.Column ... selective);
    Mono<ListingImportRun> selectByPrimaryKey(Integer id);
    Mono<Integer> updateByExampleSelective(@Param("record") ListingImportRun record, @Param("example") ListingImportRunExample example);
    Mono<Integer> updateByExample(@Param("record") ListingImportRun record, @Param("example") ListingImportRunExample example);
    Mono<Integer> updateByPrimaryKeySelective(ListingImportRun record);
    Mono<Integer> updateByPrimaryKey(ListingImportRun record);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<ListingImportRun> selectPageByExample(@Param("example") ListingImportRunExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<ListingImportRun> selectPageByExampleSelective(@Param("example") ListingImportRunExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") ListingImportRun.Column... selective);
}
