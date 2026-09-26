package org.linlinjava.announce.listing.application;

import org.linlinjava.announce.listing.domain.CrawledListingRepository;
import org.linlinjava.announce.search.SearchIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Full pass over the ACTIVE listings in batches, pushing every document again: used after an index rebuild. */
@Service
public class ListingReindexService {

    private static final Logger log = LoggerFactory.getLogger(ListingReindexService.class);

    private final CrawledListingRepository listings;
    private final SearchIndexer indexer;
    private final int batch;

    public ListingReindexService(CrawledListingRepository listings, SearchIndexer indexer,
                                 @Value("${announce.listing.reindex.batch:500}") int batch) {
        this.listings = listings;
        this.indexer = indexer;
        this.batch = batch;
    }

    /** Number of documents indexed. */
    public Mono<Long> reindexActive() {
        return page(0).doOnNext(n -> log.info("reindexed {} crawled listings", n));
    }

    private Mono<Long> page(long offset) {
        return listings.activePage(offset, batch).collectList()
                .flatMap(rows -> Flux.fromIterable(rows)
                        .concatMap(l -> { ListingDocument d = ListingDocument.of(l); return indexer.index(d.id(), d.data()); })
                        .then(rows.size() < batch ? Mono.just((long) rows.size())
                                : page(offset + batch).map(rest -> rest + rows.size())));
    }
}
