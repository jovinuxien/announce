package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.announce.db.domain.LitemallOrder;
import org.linlinjava.announce.db.domain.OrderVo;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/** Hand-written order statements: optimistic-lock update and the admin order list with its goods lines. */
public interface OrderMapper {
    /** @return 1 when the row was updated, 0 when {@code lastUpdateTime} no longer matches (concurrent change). */
    Mono<Integer> updateWithOptimisticLocker(@Param("lastUpdateTime") LocalDateTime lastUpdateTime,
                                             @Param("order") LitemallOrder order);

    Flux<Map<String, Object>> getOrderIds(@Param("query") String query, @Param("orderByClause") String orderByClause);

    Flux<OrderVo> getOrderList(@Param("query") String query, @Param("orderByClause") String orderByClause);
}
