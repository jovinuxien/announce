package org.linlinjava.announce.listing.application;

import org.linlinjava.announce.listing.domain.CrawledListingRepository;
import org.linlinjava.announce.listing.domain.ListingStatus;
import org.linlinjava.announce.search.SearchIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Marks listings the crawler has not seen for {@code announce.listing.gone-after} (default 7 days) GONE and removes
 * them from the index. A listing that shows up in a later feed goes back to ACTIVE through the importer.
 */
@Service
public class ListingSweepService {

    private static final Logger log = LoggerFactory.getLogger(ListingSweepService.class);

    private final CrawledListingRepository listings;
    private final SearchIndexer indexer;
    private final Duration goneAfter;
    private final Clock clock;

    public ListingSweepService(CrawledListingRepository listings, SearchIndexer indexer,
                               @Value("${announce.listing.gone-after:P7D}") Duration goneAfter, Clock clock) {
        this.listings = listings;
        this.indexer = indexer;
        this.goneAfter = goneAfter;
        this.clock = clock;
    }

    public Duration goneAfter() {
        return goneAfter;
    }

    /** Number of listings set GONE in this pass (at most {@code limit}). */
    public Mono<Long> sweep(int limit) {
        LocalDateTime now = LocalDateTime.now(clock);
        return listings.staleIds(now.minus(goneAfter), limit)
                .concatMap(id -> listings.updateStatus(id, ListingStatus.GONE, now).then(indexer.remove(id)).thenReturn(id))
                .count()
                .doOnNext(n -> { if (n > 0) log.info("{} crawled listings gone (not seen for {})", n, goneAfter); });
    }
}
