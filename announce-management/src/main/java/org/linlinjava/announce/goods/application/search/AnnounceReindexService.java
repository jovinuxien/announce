package org.linlinjava.announce.goods.application.search;

import org.linlinjava.announce.goods.domain.announce.AnnounceQuery;
import org.linlinjava.announce.goods.domain.announce.AnnounceReadRepository;
import org.linlinjava.announce.goods.domain.announce.AnnounceStatus;
import org.linlinjava.announce.goods.domain.announce.AnnounceSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Full pass over the PUBLISHED announces of the read model, pushing every document again. Used after an index
 * rebuild, and for rows that never went through the event store (the seed data): the search projection only
 * indexes on events, so a fresh index knows nothing about them until this runs.
 */
@Service
public class AnnounceReindexService {

    private static final Logger log = LoggerFactory.getLogger(AnnounceReindexService.class);
    private static final int PAGE = AnnounceQuery.MAX_SIZE;

    private final AnnounceReadRepository readModel;
    private final AnnounceIndexer indexer;

    public AnnounceReindexService(AnnounceReadRepository readModel, AnnounceIndexer indexer) {
        this.readModel = readModel;
        this.indexer = indexer;
    }

    /** Number of documents indexed. */
    public Mono<Long> reindexPublished() {
        return page(1).doOnNext(n -> log.info("reindexed {} published announces", n));
    }

    private Mono<Long> page(int page) {
        AnnounceQuery query = new AnnounceQuery(null, null, null, null, null, null, null, AnnounceStatus.PUBLISHED, page, PAGE, "oldest");
        return readModel.browse(query).map(AnnounceSummary::id).collectList()
                .flatMap(ids -> Flux.fromIterable(ids)
                        .concatMap(id -> readModel.findById(id)
                                .filter(a -> a.isVisible())
                                .flatMap(a -> indexer.index(AnnounceDocument.of(a)).thenReturn(1L))
                                .defaultIfEmpty(0L))
                        .reduce(0L, Long::sum)
                        .flatMap(done -> ids.size() < PAGE ? Mono.just(done) : page(page + 1).map(rest -> rest + done)));
    }
}
