package org.linlinjava.announce.db.dao.reactive;

import org.apache.ibatis.annotations.Param;
import reactor.core.publisher.Mono;

/** Atomic stock movements on {@code announce_goods_product}. */
public interface GoodsProductMapper {
    Mono<Integer> addStock(@Param("id") Integer id, @Param("num") Short num);

    /** @return 0 when the stock is insufficient (no row updated). */
    Mono<Integer> reduceStock(@Param("id") Integer id, @Param("num") Short num);
}
