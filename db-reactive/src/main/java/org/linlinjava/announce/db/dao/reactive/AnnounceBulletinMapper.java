package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.AnnounceBulletin;
import org.linlinjava.announce.db.domain.AnnounceBulletinExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_bulletin}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link AnnounceBulletinExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/AnnounceBulletinMapper.xml}.
 */
public interface AnnounceBulletinMapper {
    Mono<Long> countByExample(AnnounceBulletinExample example);
    Mono<Integer> deleteByExample(AnnounceBulletinExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(AnnounceBulletin record);
    Mono<Integer> insertSelective(AnnounceBulletin record);
    Mono<AnnounceBulletin> selectOneByExample(AnnounceBulletinExample example);
    Mono<AnnounceBulletin> selectOneByExampleSelective(@Param("example") AnnounceBulletinExample example, @Param("selective") AnnounceBulletin.Column ... selective);
    Flux<AnnounceBulletin> selectByExampleSelective(@Param("example") AnnounceBulletinExample example, @Param("selective") AnnounceBulletin.Column ... selective);
    Flux<AnnounceBulletin> selectByExample(AnnounceBulletinExample example);
    Mono<AnnounceBulletin> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") AnnounceBulletin.Column ... selective);
    Mono<AnnounceBulletin> selectByPrimaryKey(Integer id);
    Mono<AnnounceBulletin> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") AnnounceBulletin record, @Param("example") AnnounceBulletinExample example);
    Mono<Integer> updateByExample(@Param("record") AnnounceBulletin record, @Param("example") AnnounceBulletinExample example);
    Mono<Integer> updateByPrimaryKeySelective(AnnounceBulletin record);
    Mono<Integer> updateByPrimaryKey(AnnounceBulletin record);
    Mono<Integer> logicalDeleteByExample(@Param("example") AnnounceBulletinExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceBulletin> selectPageByExample(@Param("example") AnnounceBulletinExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceBulletin> selectPageByExampleSelective(@Param("example") AnnounceBulletinExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") AnnounceBulletin.Column... selective);
}
