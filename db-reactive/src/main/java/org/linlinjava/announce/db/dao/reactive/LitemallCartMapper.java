package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallCart;
import org.linlinjava.announce.db.domain.LitemallCartExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_cart}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallCartExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallCartMapper.xml}.
 */
public interface LitemallCartMapper {
    Mono<Long> countByExample(LitemallCartExample example);
    Mono<Integer> deleteByExample(LitemallCartExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallCart record);
    Mono<Integer> insertSelective(LitemallCart record);
    Mono<LitemallCart> selectOneByExample(LitemallCartExample example);
    Mono<LitemallCart> selectOneByExampleSelective(@Param("example") LitemallCartExample example, @Param("selective") LitemallCart.Column ... selective);
    Flux<LitemallCart> selectByExampleSelective(@Param("example") LitemallCartExample example, @Param("selective") LitemallCart.Column ... selective);
    Flux<LitemallCart> selectByExample(LitemallCartExample example);
    Mono<LitemallCart> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallCart.Column ... selective);
    Mono<LitemallCart> selectByPrimaryKey(Integer id);
    Mono<LitemallCart> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallCart record, @Param("example") LitemallCartExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallCart record, @Param("example") LitemallCartExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallCart record);
    Mono<Integer> updateByPrimaryKey(LitemallCart record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallCartExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallCart> selectPageByExample(@Param("example") LitemallCartExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallCart> selectPageByExampleSelective(@Param("example") LitemallCartExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallCart.Column... selective);
}
