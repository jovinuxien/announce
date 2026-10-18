package org.linlinjava.announce.listing.application;

import org.linlinjava.announce.listing.domain.CrawledListing;
import org.linlinjava.announce.listing.domain.CrawledListingRepository;
import org.linlinjava.announce.search.SearchIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Full pass over the ACTIVE listings in batches, pushing every document whose grade passes {@link IndexGating} again:
 * used after an index rebuild. Rows imported before grading existed are graded and stored on the way, so one reindex
 * migrates them.
 */
@Service
public class ListingReindexService {

    private static final Logger log = LoggerFactory.getLogger(ListingReindexService.class);

    private final CrawledListingRepository listings;
    private final SearchIndexer indexer;
    private final IndexGating gating;
    private final int batch;
    private final Clock clock;

    public ListingReindexService(CrawledListingRepository listings, SearchIndexer indexer, IndexGating gating,
                                 @Value("${announce.listing.reindex.batch:500}") int batch, Clock clock) {
        this.listings = listings;
        this.indexer = indexer;
        this.gating = gating;
        this.batch = batch;
        this.clock = clock;
    }

    /** Number of documents indexed. */
    public Mono<Long> reindexActive() {
        return page(0).doOnNext(n -> log.info("reindexed {} crawled listings", n));
    }

    private Mono<Long> page(long offset) {
        return listings.activePage(offset, batch).collectList()
                .flatMap(rows -> Flux.fromIterable(rows)
                        .concatMap(this::reindexOne)
                        .reduce(0L, Long::sum)
                        .flatMap(n -> rows.size() < batch ? Mono.just(n) : page(offset + batch).map(rest -> rest + n)));
    }

    private Mono<Long> reindexOne(CrawledListing row) {
        CrawledListing l = row.graded();
        Mono<Void> grade = l == row ? Mono.empty() : listings.updateQuality(l.id(), l.quality(), LocalDateTime.now(clock));
        if (!gating.indexable(l)) {
            return grade.then(indexer.remove(l.id())).thenReturn(0L);
        }
        ListingDocument d = ListingDocument.of(l);
        return grade.then(indexer.index(d.id(), d.data())).thenReturn(1L);
    }
}
