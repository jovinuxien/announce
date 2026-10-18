package org.linlinjava.announce.listing.application;

import org.linlinjava.announce.listing.domain.Address;
import org.linlinjava.announce.listing.domain.CrawledItem;
import org.linlinjava.announce.listing.domain.CrawledListing;
import org.linlinjava.announce.listing.domain.CrawledListingRepository;
import org.linlinjava.announce.listing.domain.ListingCategoryMapping;
import org.linlinjava.announce.listing.domain.ListingFeedParser;
import org.linlinjava.announce.listing.domain.ListingImportRun;
import org.linlinjava.announce.listing.domain.ListingImportRunRepository;
import org.linlinjava.announce.listing.domain.ListingSource;
import org.linlinjava.announce.listing.domain.RegionLookup;
import org.linlinjava.announce.search.SearchIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Upserts crawled items into {@code crawled_listing} and keeps the search index in step. Transport-agnostic: the
 * core takes a {@link Flux} of normalised items, so a file (today, via the Spring Integration polling flow and the
 * admin endpoint) or a Kafka topic (later) feed the same path.
 *
 * <p>Per item: unknown id -> insert, grade, index when the grade passes {@link IndexGating}; known with a different
 * content hash, or GONE -> overwrite, back to ACTIVE, regrade, index (or drop from the index when the grade no longer
 * passes); known and unchanged -> touch {@code last_seen_at} only. Items are processed one at a time, in feed
 * order, so the same run is safe to repeat: the second pass touches every row and indexes nothing. A file import is
 * recorded in {@code listing_import_run} for the inventory.
 */
@Service
public class ListingImportService {

    private static final Logger log = LoggerFactory.getLogger(ListingImportService.class);
    private static final Retry INDEX_RETRY = Retry.backoff(2, Duration.ofMillis(500));

    private final CrawledListingRepository listings;
    private final ListingImportRunRepository runs;
    private final ListingCategoryMapping categories;
    private final RegionLookup regions;
    private final SearchIndexer indexer;
    private final IndexGating gating;
    private final Map<ListingSource, ListingFeedParser> parsers = new EnumMap<>(ListingSource.class);
    private final Clock clock;

    public ListingImportService(CrawledListingRepository listings, ListingImportRunRepository runs, ListingCategoryMapping categories,
                                RegionLookup regions, SearchIndexer indexer, IndexGating gating, List<ListingFeedParser> parsers, Clock clock) {
        this.listings = listings;
        this.runs = runs;
        this.categories = categories;
        this.regions = regions;
        this.indexer = indexer;
        this.gating = gating;
        parsers.forEach(p -> this.parsers.put(p.source(), p));
        this.clock = clock;
    }

    /** Parses one feed file of a source, imports its items and records the run. */
    public Mono<ImportReport> importFile(ListingSource source, Path file) {
        ListingFeedParser parser = parsers.get(source);
        if (parser == null) {
            return Mono.error(new IllegalArgumentException("no feed parser for source " + source));
        }
        log.info("importing {} feed {}", source, file);
        LocalDateTime started = LocalDateTime.now(clock);
        return importItems(parser.parse(file))
                .flatMap(r -> runs.record(new ListingImportRun(source, file.toString(), started, LocalDateTime.now(clock),
                        r.seen(), r.inserted(), r.updated(), r.unchanged(), r.rejected(), r.good(), r.weak(), r.reject())).thenReturn(r))
                .doOnNext(r -> log.info("imported {} feed {}: {}", source, file.getFileName(), r));
    }

    /** Imports normalised items from any transport. */
    public Mono<ImportReport> importItems(Flux<CrawledItem> items) {
        return items.concatMap(this::importOne)
                .reduce(ImportReport.EMPTY, ImportReport::plus);
    }

    Mono<ImportReport> importOne(CrawledItem item) {
        LocalDateTime now = LocalDateTime.now(clock);
        return listings.findById(item.id())
                .map(Optional::of).defaultIfEmpty(Optional.empty())
                .flatMap(existing -> {
                    if (existing.isEmpty()) {
                        return resolve(item, now, null).flatMap(l -> saveAndIndex(l, false)
                                .map(outcome -> ImportReport.oneInserted(l.quality(), outcome)));
                    }
                    CrawledListing row = existing.get();
                    if (row.isActive() && !row.contentChanged(item)) {
                        return listings.touchSeen(row.id(), now).thenReturn(ImportReport.oneUnchanged());
                    }
                    return resolve(item, now, row).flatMap(l -> saveAndIndex(l, gating.indexable(row))
                            .map(outcome -> ImportReport.oneUpdated(l.quality(), outcome)));
                })
                .onErrorResume(e -> {
                    log.warn("listing {} rejected: {}", item.id(), e.toString());
                    return Mono.just(ImportReport.oneRejected());
                });
    }

    private Mono<CrawledListing> resolve(CrawledItem item, LocalDateTime now, CrawledListing existing) {
        Address address = Address.parse(item.addressRaw());
        Mono<Optional<Integer>> region = address.city() == null ? Mono.just(Optional.empty())
                : regions.regionIdByCity(address.city()).map(Optional::of).defaultIfEmpty(Optional.empty());
        Mono<Optional<Integer>> category = item.sourceCategory() == null ? Mono.just(Optional.empty())
                : categories.categoryFor(item.source(), item.sourceCategory()).map(Optional::of).defaultIfEmpty(Optional.empty());
        return Mono.zip(region, category).map(t -> existing == null
                ? CrawledListing.firstSeen(item, t.getT1().orElse(null), t.getT2().orElse(null), now)
                : existing.seenAgain(item, t.getT1().orElse(null), t.getT2().orElse(null), now));
    }

    /** Saves the row; indexes it when its grade passes, removes the stale document when it was indexed and no longer passes. */
    private Mono<ImportReport.IndexOutcome> saveAndIndex(CrawledListing listing, boolean wasIndexed) {
        Mono<Void> saved = listings.save(listing);
        if (gating.indexable(listing)) {
            ListingDocument doc = ListingDocument.of(listing);
            return saved.then(indexer.index(doc.id(), doc.data()).retryWhen(INDEX_RETRY))
                    .thenReturn(ImportReport.IndexOutcome.INDEXED)
                    .onErrorResume(e -> {
                        log.error("listing {} stored but not indexed after retries: {}", listing.id(), e.toString());
                        return Mono.just(ImportReport.IndexOutcome.FAILED);
                    });
        }
        log.info("listing {} graded {} {}: not indexed", listing.id(), listing.quality().grade(), listing.quality().issues());
        return (wasIndexed ? saved.then(indexer.remove(listing.id()).retryWhen(INDEX_RETRY)) : saved)
                .thenReturn(ImportReport.IndexOutcome.SKIPPED);
    }
}
