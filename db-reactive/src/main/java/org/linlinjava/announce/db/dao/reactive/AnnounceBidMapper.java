package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.AnnounceBid;
import org.linlinjava.announce.db.domain.AnnounceBidExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_bid}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link AnnounceBidExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/AnnounceBidMapper.xml}.
 */
public interface AnnounceBidMapper {
    Mono<Long> countByExample(AnnounceBidExample example);
    Mono<Integer> deleteByExample(AnnounceBidExample example);
    Mono<Integer> deleteByPrimaryKey(String id);
    Mono<Integer> insert(AnnounceBid record);
    Mono<Integer> insertSelective(AnnounceBid record);
    Mono<AnnounceBid> selectOneByExample(AnnounceBidExample example);
    Mono<AnnounceBid> selectOneByExampleSelective(@Param("example") AnnounceBidExample example, @Param("selective") AnnounceBid.Column ... selective);
    Flux<AnnounceBid> selectByExampleSelective(@Param("example") AnnounceBidExample example, @Param("selective") AnnounceBid.Column ... selective);
    Flux<AnnounceBid> selectByExample(AnnounceBidExample example);
    Mono<AnnounceBid> selectByPrimaryKeySelective(@Param("id") String id, @Param("selective") AnnounceBid.Column ... selective);
    Mono<AnnounceBid> selectByPrimaryKey(String id);
    Mono<AnnounceBid> selectByPrimaryKeyWithLogicalDelete(@Param("id") String id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") AnnounceBid record, @Param("example") AnnounceBidExample example);
    Mono<Integer> updateByExample(@Param("record") AnnounceBid record, @Param("example") AnnounceBidExample example);
    Mono<Integer> updateByPrimaryKeySelective(AnnounceBid record);
    Mono<Integer> updateByPrimaryKey(AnnounceBid record);
    Mono<Integer> logicalDeleteByExample(@Param("example") AnnounceBidExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(String id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceBid> selectPageByExample(@Param("example") AnnounceBidExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceBid> selectPageByExampleSelective(@Param("example") AnnounceBidExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") AnnounceBid.Column... selective);
}
