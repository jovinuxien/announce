package org.linlinjava.announce.gatewayadmin.web.admin;

import org.linlinjava.announce.db.dao.reactive.AdminDashboardMapper;
import org.linlinjava.announce.db.dao.reactive.CrawledListingMapper;
import org.linlinjava.announce.db.domain.CrawledListing;
import org.linlinjava.announce.db.domain.CrawledListingExample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Crawled-listing statistics for the admin SPA (the import and reindex triggers stay in announce-management under
 * {@code /api/admin/listings/**}): counts per source and status, the most recently gone rows and the most clicked.
 */
@RestController
@RequestMapping("/api/admin/listing-stats")
public class EdgeListingStatsController {

    private final AdminDashboardMapper stats;
    private final CrawledListingMapper listings;

    public EdgeListingStatsController(AdminDashboardMapper stats, CrawledListingMapper listings) {
        this.stats = stats;
        this.listings = listings;
    }

    public record ListingRow(String id, String source, String title, String url, BigDecimal price, String currency, String status,
                             Integer clickCount, LocalDateTime lastSeenAt, LocalDateTime publishedAt) {
        static ListingRow of(CrawledListing l) {
            return new ListingRow(l.getId(), l.getSource(), l.getTitle(), l.getUrl(), l.getPrice(), l.getCurrency(), l.getStatus(),
                    l.getClickCount(), l.getLastSeenAt(), l.getPublishedAt());
        }
    }

    public record ListingStats(List<EdgeDashboardController.ListingCount> counts, long clicks, List<ListingRow> recentGone, List<ListingRow> topClicked) {}

    @GetMapping
    public Mono<ListingStats> stats(@RequestParam(defaultValue = "10") int limit) {
        int n = Math.max(1, Math.min(limit, 50));
        CrawledListingExample gone = new CrawledListingExample();
        gone.or().andStatusEqualTo("GONE");
        gone.setOrderByClause("update_time desc");
        CrawledListingExample clicked = new CrawledListingExample();
        clicked.or().andClickCountGreaterThan(0);
        clicked.setOrderByClause("click_count desc, update_time desc");
        return Mono.zip(
                stats.listingCounts().map(r -> new EdgeDashboardController.ListingCount(EdgeDashboardController.str(r, "source"),
                        EdgeDashboardController.str(r, "status"), EdgeDashboardController.num(r, "total"))).collectList(),
                stats.listingClickTotal().defaultIfEmpty(0L),
                listings.selectPageByExample(gone, 0, n).map(ListingRow::of).collectList(),
                listings.selectPageByExample(clicked, 0, n).map(ListingRow::of).collectList())
                .map(t -> new ListingStats(t.getT1(), t.getT2(), t.getT3(), t.getT4()));
    }
}
