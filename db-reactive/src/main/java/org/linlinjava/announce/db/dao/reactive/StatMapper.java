package org.linlinjava.announce.db.dao.reactive;

import reactor.core.publisher.Flux;

import java.util.Map;

/** Daily statistics for the admin dashboard; each row is keyed by column alias (day, users, orders, ...). */
public interface StatMapper {
    Flux<Map<String, Object>> statUser();

    /** Paid deals per day from the order module's read model: orders, distinct buyers, amount, amount per buyer. */
    Flux<Map<String, Object>> statOrder();
}
