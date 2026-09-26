package org.linlinjava.announce.goods.domain.announce;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Query port over the announce read model ({@code announce} and its detail tables), which the projections keep
 * up to date from the events. Queries never touch the event store.
 */
public interface AnnounceReadRepository {

    /** The announce as the read model knows it, with its kind-specific details; empty when unknown. */
    Mono<Announce> findById(String id);

    /**
     * The announce together with the id of the last event the read-model projection applied to it, read from one
     * row so both are from the same snapshot; empty when unknown. Other projections use it to check they are not
     * ahead of the read model before they derive from it.
     */
    Mono<Applied> findApplied(String id);

    record Applied(Announce announce, String lastEventId) {
    }

    Flux<AnnounceSummary> browse(AnnounceQuery query);

    Mono<Long> count(AnnounceQuery query);

    /** Ids of published announces whose expiry date has passed, oldest first. */
    Flux<String> findPublishedExpiredBefore(LocalDateTime now, int limit);
}
