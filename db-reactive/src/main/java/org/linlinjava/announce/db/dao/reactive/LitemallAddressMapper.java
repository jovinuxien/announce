package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallAddress;
import org.linlinjava.announce.db.domain.LitemallAddressExample;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive (R2DBC) mapper for {@code announce_address}, migrated from the blocking MyBatis generator output.
 * Method names and the {@link LitemallAddressExample} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {@code org/linlinjava/announce/db/dao/reactive/LitemallAddressMapper.xml}.
 */
public interface LitemallAddressMapper {
    Mono<Long> countByExample(LitemallAddressExample example);
    Mono<Integer> deleteByExample(LitemallAddressExample example);
    Mono<Integer> deleteByPrimaryKey(Integer id);
    Mono<Integer> insert(LitemallAddress record);
    Mono<Integer> insertSelective(LitemallAddress record);
    Mono<LitemallAddress> selectOneByExample(LitemallAddressExample example);
    Mono<LitemallAddress> selectOneByExampleSelective(@Param("example") LitemallAddressExample example, @Param("selective") LitemallAddress.Column ... selective);
    Flux<LitemallAddress> selectByExampleSelective(@Param("example") LitemallAddressExample example, @Param("selective") LitemallAddress.Column ... selective);
    Flux<LitemallAddress> selectByExample(LitemallAddressExample example);
    Mono<LitemallAddress> selectByPrimaryKeySelective(@Param("id") Integer id, @Param("selective") LitemallAddress.Column ... selective);
    Mono<LitemallAddress> selectByPrimaryKey(Integer id);
    Mono<LitemallAddress> selectByPrimaryKeyWithLogicalDelete(@Param("id") Integer id, @Param("andLogicalDeleted") boolean andLogicalDeleted);
    Mono<Integer> updateByExampleSelective(@Param("record") LitemallAddress record, @Param("example") LitemallAddressExample example);
    Mono<Integer> updateByExample(@Param("record") LitemallAddress record, @Param("example") LitemallAddressExample example);
    Mono<Integer> updateByPrimaryKeySelective(LitemallAddress record);
    Mono<Integer> updateByPrimaryKey(LitemallAddress record);
    Mono<Integer> logicalDeleteByExample(@Param("example") LitemallAddressExample example);
    Mono<Integer> logicalDeleteByPrimaryKey(Integer id);

    /** {@link #selectByExample} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallAddress> selectPageByExample(@Param("example") LitemallAddressExample example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {@link #selectByExampleSelective} with an explicit {@code LIMIT limit OFFSET offset}. */
    Flux<LitemallAddress> selectPageByExampleSelective(@Param("example") LitemallAddressExample example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") LitemallAddress.Column... selective);
}
