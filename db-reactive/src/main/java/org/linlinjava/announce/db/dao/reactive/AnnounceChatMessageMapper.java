package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.AnnounceChatMessage;
import org.linlinjava.announce.db.domain.AnnounceChatMessageExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_chat_message}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link AnnounceChatMessageExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/AnnounceChatMessageMapper.xml}.
 */
public interface AnnounceChatMessageMapper {
    Mono<Long> countByExample(AnnounceChatMessageExample example);
    Mono<Integer> deleteByExample(AnnounceChatMessageExample example);
    Mono<Integer> deleteByPrimaryKey(String id);
    Mono<Integer> insert(AnnounceChatMessage record);
    Mono<Integer> insertSelective(AnnounceChatMessage record);
    Mono<AnnounceChatMessage> selectOneByExample(AnnounceChatMessageExample example);
    Mono<AnnounceChatMessage> selectOneByExampleSelective(@Param("example") AnnounceChatMessageExample example, @Param("selective") AnnounceChatMessage.Column ... selective);
    Mono<AnnounceChatMessage> selectOneByExampleWithBLOBs(AnnounceChatMessageExample example);
    Flux<AnnounceChatMessage> selectByExampleSelective(@Param("example") AnnounceChatMessageExample example, @Param("selective") AnnounceChatMessage.Column ... selective);
    Flux<AnnounceChatMessage> selectByExampleWithBLOBs(AnnounceChatMessageExample example);
    Flux<AnnounceChatMessage> selectByExample(AnnounceChatMessageExample example);
    Mono<AnnounceChatMessage> selectByPrimaryKeySelective(@Param("id") String id, @Param("selective") AnnounceChatMessage.Column ... selective);
    Mono<AnnounceChatMessage> selectByPrimaryKey(String id);
    Mono<AnnounceChatMessage> selectByPrimaryKeyWithLogicalDelete(@Param("id") String id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") AnnounceChatMessage record, @Param("example") AnnounceChatMessageExample example);
    Mono<Integer> updateByExampleWithBLOBs(@Param("record") AnnounceChatMessage record, @Param("example") AnnounceChatMessageExample example);
    Mono<Integer> updateByExample(@Param("record") AnnounceChatMessage record, @Param("example") AnnounceChatMessageExample example);
    Mono<Integer> updateByPrimaryKeySelective(AnnounceChatMessage record);
    Mono<Integer> updateByPrimaryKeyWithBLOBs(AnnounceChatMessage record);
    Mono<Integer> updateByPrimaryKey(AnnounceChatMessage record);
    Mono<Integer> logicalDeleteByExample(@Param("example") AnnounceChatMessageExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(String id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceChatMessage> selectPageByExample(@Param("example") AnnounceChatMessageExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<AnnounceChatMessage> selectPageByExampleSelective(@Param("example") AnnounceChatMessageExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") AnnounceChatMessage.Column... selective);

    /** Inserts the message or overwrites the existing row with the same id (MessagePosted replays). */
    Mono<Integer> upsert(AnnounceChatMessage record);

    /** Sets offer_status of one OFFER message (OPEN, ACCEPTED, DECLINED, SUPERSEDED). */
    Mono<Integer> updateOfferStatus(@Param("id") String id,
                                    @Param("offerStatus") String offerStatus,
                                    @Param("updateTime") java.time.LocalDateTime updateTime);

    /** Messages of a conversation with seq greater than {@code afterSeq}, in seq order, content included. */
    Flux<AnnounceChatMessage> selectAfter(@Param("conversationId") String conversationId,
                                          @Param("afterSeq") int afterSeq,
                                          @Param("limit") int limit);
}
