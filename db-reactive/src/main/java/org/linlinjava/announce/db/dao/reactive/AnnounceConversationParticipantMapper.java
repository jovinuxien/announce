package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.AnnounceConversationParticipant;
import org.linlinjava.announce.db.domain.AnnounceConversationParticipantExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_conversation_participant}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link AnnounceConversationParticipantExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/AnnounceConversationParticipantMapper.xml}.
 */
public interface AnnounceConversationParticipantMapper {
    Mono<Long> countByExample(AnnounceConversationParticipantExample example);
    Mono<Integer> deleteByExample(AnnounceConversationParticipantExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(AnnounceConversationParticipant record);
    Mono<Integer> insertSelective(AnnounceConversationParticipant record);
    Mono<AnnounceConversationParticipant> selectOneByExample(AnnounceConversationParticipantExample example);
    Mono<AnnounceConversationParticipant> selectOneByExampleSelective(@Param("example") AnnounceConversationParticipantExample example, @Param("selective") AnnounceConversationParticipant.Column ... selective);
    Flux<AnnounceConversationParticipant> selectByExampleSelective(@Param("example") AnnounceConversationParticipantExample example, @Param("selective") AnnounceConversationParticipant.Column ... selective);
    Flux<AnnounceConversationParticipant> selectByExample(AnnounceConversationParticipantExample example);
    Mono<AnnounceConversationParticipant> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") AnnounceConversationParticipant.Column ... selective);
    Mono<AnnounceConversationParticipant> selectByPrimaryKey(Integer id);
    Mono<AnnounceConversationParticipant> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") AnnounceConversationParticipant record, @Param("example") AnnounceConversationParticipantExample example);
    Mono<Integer> updateByExample(@Param("record") AnnounceConversationParticipant record, @Param("example") AnnounceConversationParticipantExample example);
    Mono<Integer> updateByPrimaryKeySelective(AnnounceConversationParticipant record);
    Mono<Integer> updateByPrimaryKey(AnnounceConversationParticipant record);
    Mono<Integer> logicalDeleteByExample(@Param("example") AnnounceConversationParticipantExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceConversationParticipant> selectPageByExample(@Param("example") AnnounceConversationParticipantExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceConversationParticipant> selectPageByExampleSelective(@Param("example") AnnounceConversationParticipantExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") AnnounceConversationParticipant.Column... selective);

    /** Inserts the participant or refreshes role / joined_time of the existing one (unique on conversation, type, id). */
    Mono<Integer> upsert(AnnounceConversationParticipant record);

    /** Raises last_read_seq to {@code seq} when higher (MarkRead is a read-model write, never an event). */
    Mono<Integer> raiseLastReadSeq(@Param("conversationId") String conversationId,
                                   @Param("participantType") String participantType,
                                   @Param("participantId") Integer participantId,
                                   @Param("seq") Integer seq,
                                   @Param("updateTime") java.time.LocalDateTime updateTime);
}
