package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.ListingCategoryMap;
import org.linlinjava.announce.db.domain.ListingCategoryMapExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.linlinjava.announce.db.domain.ListingCategoryMapKey;

/**
 * Reactive (R2DBC) mapper for {@code listing_category_map}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link ListingCategoryMapExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/ListingCategoryMapMapper.xml}.
 */
public interface ListingCategoryMapMapper {
    Mono<Long> countByExample(ListingCategoryMapExample example);
    Mono<Integer> deleteByExample(ListingCategoryMapExample example);
    Mono<Integer> deleteByPrimaryKey(ListingCategoryMapKey key);
    Mono<Integer> insert(ListingCategoryMap record);
    Mono<Integer> insertSelective(ListingCategoryMap record);
    Mono<ListingCategoryMap> selectOneByExample(ListingCategoryMapExample example);
    Mono<ListingCategoryMap> selectOneByExampleSelective(@Param("example") ListingCategoryMapExample example, @Param("selective") ListingCategoryMap.Column ... selective);
    Flux<ListingCategoryMap> selectByExampleSelective(@Param("example") ListingCategoryMapExample example, @Param("selective") ListingCategoryMap.Column ... selective);
    Flux<ListingCategoryMap> selectByExample(ListingCategoryMapExample example);
    Mono<ListingCategoryMap> selectByPrimaryKeySelective(@Param("record") ListingCategoryMapKey key, @Param("selective") ListingCategoryMap.Column ... selective);
    Mono<ListingCategoryMap> selectByPrimaryKey(ListingCategoryMapKey key);
    Mono<Integer> updateByExampleSelective(@Param("record") ListingCategoryMap record, @Param("example") ListingCategoryMapExample example);
    Mono<Integer> updateByExample(@Param("record") ListingCategoryMap record, @Param("example") ListingCategoryMapExample example);
    Mono<Integer> updateByPrimaryKeySelective(ListingCategoryMap record);
    Mono<Integer> updateByPrimaryKey(ListingCategoryMap record);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<ListingCategoryMap> selectPageByExample(@Param("example") ListingCategoryMapExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<ListingCategoryMap> selectPageByExampleSelective(@Param("example") ListingCategoryMapExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") ListingCategoryMap.Column... selective);
}
