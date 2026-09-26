package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.AnnounceConversation;
import org.linlinjava.announce.db.domain.AnnounceConversationExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_conversation}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link AnnounceConversationExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/AnnounceConversationMapper.xml}.
 */
public interface AnnounceConversationMapper {
    Mono<Long> countByExample(AnnounceConversationExample example);
    Mono<Integer> deleteByExample(AnnounceConversationExample example);
    Mono<Integer> deleteByPrimaryKey(String id);
    Mono<Integer> insert(AnnounceConversation record);
    Mono<Integer> insertSelective(AnnounceConversation record);
    Mono<AnnounceConversation> selectOneByExample(AnnounceConversationExample example);
    Mono<AnnounceConversation> selectOneByExampleSelective(@Param("example") AnnounceConversationExample example, @Param("selective") AnnounceConversation.Column ... selective);
    Flux<AnnounceConversation> selectByExampleSelective(@Param("example") AnnounceConversationExample example, @Param("selective") AnnounceConversation.Column ... selective);
    Flux<AnnounceConversation> selectByExample(AnnounceConversationExample example);
    Mono<AnnounceConversation> selectByPrimaryKeySelective(@Param("id") String id, @Param("selective") AnnounceConversation.Column ... selective);
    Mono<AnnounceConversation> selectByPrimaryKey(String id);
    Mono<AnnounceConversation> selectByPrimaryKeyWithLogicalDelete(@Param("id") String id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") AnnounceConversation record, @Param("example") AnnounceConversationExample example);
    Mono<Integer> updateByExample(@Param("record") AnnounceConversation record, @Param("example") AnnounceConversationExample example);
    Mono<Integer> updateByPrimaryKeySelective(AnnounceConversation record);
    Mono<Integer> updateByPrimaryKey(AnnounceConversation record);
    Mono<Integer> logicalDeleteByExample(@Param("example") AnnounceConversationExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(String id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceConversation> selectPageByExample(@Param("example") AnnounceConversationExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceConversation> selectPageByExampleSelective(@Param("example") AnnounceConversationExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") AnnounceConversation.Column... selective);

    /** Inserts the row or overwrites every column of the existing one (projections replay events at least once). */
    Mono<Integer> upsert(AnnounceConversation record);

    /** Rewrites the six offer columns as given (null clears them), plus last_event_id and update_time. */
    Mono<Integer> updateOffers(AnnounceConversation record);

    /** Conversations a member or staff member takes part in, newest activity first, with explicit paging. */
    Flux<AnnounceConversation> selectByParticipant(@Param("participantType") String participantType,
                                                   @Param("participantId") Integer participantId,
                                                   @Param("offset") long offset,
                                                   @Param("limit") int limit);
}
