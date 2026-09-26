package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.AnnounceRefreshToken;
import org.linlinjava.announce.db.domain.AnnounceRefreshTokenExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_refresh_token}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link AnnounceRefreshTokenExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/AnnounceRefreshTokenMapper.xml}.
 */
public interface AnnounceRefreshTokenMapper {
    Mono<Long> countByExample(AnnounceRefreshTokenExample example);
    Mono<Integer> deleteByExample(AnnounceRefreshTokenExample example);
    Mono<Integer> deleteByPrimaryKey(String id);
    Mono<Integer> insert(AnnounceRefreshToken record);
    Mono<Integer> insertSelective(AnnounceRefreshToken record);
    Mono<AnnounceRefreshToken> selectOneByExample(AnnounceRefreshTokenExample example);
    Mono<AnnounceRefreshToken> selectOneByExampleSelective(@Param("example") AnnounceRefreshTokenExample example, @Param("selective") AnnounceRefreshToken.Column ... selective);
    Flux<AnnounceRefreshToken> selectByExampleSelective(@Param("example") AnnounceRefreshTokenExample example, @Param("selective") AnnounceRefreshToken.Column ... selective);
    Flux<AnnounceRefreshToken> selectByExample(AnnounceRefreshTokenExample example);
    Mono<AnnounceRefreshToken> selectByPrimaryKeySelective(@Param("id") String id, @Param("selective") AnnounceRefreshToken.Column ... selective);
    Mono<AnnounceRefreshToken> selectByPrimaryKey(String id);
    Mono<Integer> updateByExampleSelective(@Param("record") AnnounceRefreshToken record, @Param("example") AnnounceRefreshTokenExample example);
    Mono<Integer> updateByExample(@Param("record") AnnounceRefreshToken record, @Param("example") AnnounceRefreshTokenExample example);
    Mono<Integer> updateByPrimaryKeySelective(AnnounceRefreshToken record);
    Mono<Integer> updateByPrimaryKey(AnnounceRefreshToken record);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceRefreshToken> selectPageByExample(@Param("example") AnnounceRefreshTokenExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceRefreshToken> selectPageByExampleSelective(@Param("example") AnnounceRefreshTokenExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") AnnounceRefreshToken.Column... selective);
}
