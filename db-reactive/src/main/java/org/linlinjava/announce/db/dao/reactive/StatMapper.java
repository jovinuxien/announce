package org.linlinjava.announce.db.dao.reactive;

import reactor.core.publisher.Flux;

import java.util.Map;

/** Daily statistics for the admin dashboard; each row is keyed by column alias (day, users, orders, ...). */
public interface StatMapper {
    Flux<Map<String, Object>> statUser();

    Flux<Map<String, Object>> statOrder();

    Flux<Map<String, Object>> statGoods();
}
