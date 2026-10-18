package org.linlinjava.announce.listing.application;

import org.linlinjava.announce.listing.domain.CrawledListing;
import org.linlinjava.announce.listing.domain.CrawledListingRepository;
import org.linlinjava.announce.listing.domain.LinkChecker;
import org.linlinjava.announce.listing.domain.LinkStatus;
import org.linlinjava.announce.listing.domain.ListingStatus;
import org.linlinjava.announce.search.SearchIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Objects;

/**
 * Bounded liveness check of the crawled listings: the {@code announce.listing.liveness.batch} ACTIVE rows checked
 * longest ago (never-checked first) are probed one at a time, sorted by host so one site is hit at a time, with
 * {@code announce.listing.liveness.delay} between probes. 404/410 on the source url sets the row GONE at once and
 * removes its document; a dead image downgrades the grade (and reindexes, or removes the document when the grade
 * no longer passes); an image that answers again restores it. Other statuses and unreachable hosts only stamp
 * {@code url_checked_at}, so a blocked or flaky site never empties the index.
 */
@Service
public class ListingLivenessService {

    private static final Logger log = LoggerFactory.getLogger(ListingLivenessService.class);

    private final CrawledListingRepository listings;
    private final LinkChecker links;
    private final SearchIndexer indexer;
    private final IndexGating gating;
    private final int batch;
    private final Duration delay;
    private final Clock clock;

    public ListingLivenessService(CrawledListingRepository listings, LinkChecker links, SearchIndexer indexer, IndexGating gating,
                                  @Value("${announce.listing.liveness.batch:100}") int batch,
                                  @Value("${announce.listing.liveness.delay:PT1S}") Duration delay, Clock clock) {
        this.listings = listings;
        this.links = links;
        this.indexer = indexer;
        this.gating = gating;
        this.batch = batch;
        this.delay = delay;
        this.clock = clock;
    }

    public Mono<LivenessReport> check() {
        return check(batch);
    }

    public Mono<LivenessReport> check(int limit) {
        return listings.forLivenessCheck(limit)
                .sort(Comparator.comparing((CrawledListing l) -> host(l.url())).thenComparing(CrawledListing::id))
                .concatMap(l -> checkOne(l).delayElement(delay))
                .reduce(LivenessReport.EMPTY, LivenessReport::plus)
                .doOnNext(r -> { if (r.checked() > 0) log.info("liveness: {}", r); });
    }

    Mono<LivenessReport> checkOne(CrawledListing l) {
        LocalDateTime now = LocalDateTime.now(clock);
        return links.check(l.url()).flatMap(url -> {
            if (url == LinkStatus.GONE) {
                return listings.updateStatus(l.id(), ListingStatus.GONE, now)
                        .then(listings.updateLiveness(l.id(), now, false, l.imageOk()))
                        .then(indexer.remove(l.id()))
                        .thenReturn(new LivenessReport(1, 1, 0, 0, 0));
            }
            Boolean urlOk = url == LinkStatus.OK ? Boolean.TRUE : l.urlOk();
            Mono<LinkStatus> image = l.imageUrl() == null ? Mono.just(LinkStatus.UNREACHABLE)
                    : Mono.delay(delay).then(links.check(l.imageUrl()));
            return image.flatMap(img -> {
                Boolean imageOk = switch (img) {
                    case OK -> Boolean.TRUE;
                    case GONE, ERROR -> Boolean.FALSE;
                    case UNREACHABLE -> l.imageOk();
                };
                CrawledListing checked = l.graded().checked(now, urlOk, imageOk);
                boolean verdictChanged = !Objects.equals(l.imageOk(), imageOk);
                boolean gradeChanged = l.quality() == null || !l.quality().equals(checked.quality());
                Mono<Void> record = listings.updateLiveness(l.id(), now, urlOk, imageOk);
                if (!gradeChanged) {
                    return record.thenReturn(new LivenessReport(1, 0, 0, 0, 0));
                }
                Mono<Void> update = record.then(listings.updateQuality(l.id(), checked.quality(), now)).then(reindex(checked));
                long dead = verdictChanged && Boolean.FALSE.equals(imageOk) ? 1 : 0;
                long back = verdictChanged && Boolean.TRUE.equals(imageOk) && Boolean.FALSE.equals(l.imageOk()) ? 1 : 0;
                return update.thenReturn(new LivenessReport(1, 0, dead, back, 1));
            });
        }).onErrorResume(e -> {
            log.warn("liveness check of {} failed: {}", l.id(), e.toString());
            return Mono.just(new LivenessReport(1, 0, 0, 0, 0));
        });
    }

    private Mono<Void> reindex(CrawledListing l) {
        if (gating.indexable(l)) {
            ListingDocument d = ListingDocument.of(l);
            return indexer.index(d.id(), d.data());
        }
        return indexer.remove(l.id());
    }

    private static String host(String url) {
        try {
            String h = URI.create(url).getHost();
            return h == null ? "" : h;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
