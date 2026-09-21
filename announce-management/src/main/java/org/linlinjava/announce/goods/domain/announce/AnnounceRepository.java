package org.linlinjava.announce.goods.domain.announce;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/** Persistence port of the Announce aggregate. Implemented in the infrastructure layer over the db-reactive mappers. */
public interface AnnounceRepository {

    /** Full aggregate with variants, attributes and specifications; empty when unknown. */
    Mono<Announce> findById(AnnounceId id);

    /** Inserts a new announce (assigning its id) or updates an existing one together with its children, in one transaction. */
    Mono<Announce> save(Announce announce);

    /** Published announces whose expiry date has passed, oldest first. */
    Flux<Announce> findPublishedExpiredBefore(LocalDateTime now, int limit);

    Flux<AnnounceSummary> browse(AnnounceQuery query);

    Mono<Long> count(AnnounceQuery query);
}
