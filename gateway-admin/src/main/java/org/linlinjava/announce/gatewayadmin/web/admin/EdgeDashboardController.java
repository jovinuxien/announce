package org.linlinjava.announce.gatewayadmin.web.admin;

import org.linlinjava.announce.db.dao.reactive.AdminDashboardMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Counts for the dashboard: announces per kind and status, crawled listings per source and status, members, per-day series. */
@RestController
@RequestMapping("/api/admin/dashboard")
public class EdgeDashboardController {

    private final AdminDashboardMapper stats;

    public EdgeDashboardController(AdminDashboardMapper stats) {
        this.stats = stats;
    }

    public record AnnounceCount(String kind, String status, long total) {}

    public record ListingCount(String source, String status, long total) {}

    public record DayCount(String day, long total) {}

    public record Dashboard(List<AnnounceCount> announces, List<ListingCount> listings, long members, long admins, long categories, long brands,
                            long listingClicks, List<DayCount> newMembersPerDay, List<DayCount> newAnnouncesPerDay, LocalDateTime generatedAt) {}

    @GetMapping
    public Mono<Dashboard> dashboard(@RequestParam(defaultValue = "30") int days) {
        int window = Math.max(1, Math.min(days, 365));
        Mono<List<AnnounceCount>> announces = stats.announceCounts()
                .map(r -> new AnnounceCount(str(r, "kind"), str(r, "status"), num(r, "total"))).collectList();
        Mono<List<ListingCount>> listings = stats.listingCounts()
                .map(r -> new ListingCount(str(r, "source"), str(r, "status"), num(r, "total"))).collectList();
        Mono<List<DayCount>> members = stats.newMembersPerDay(window).map(r -> new DayCount(str(r, "day"), num(r, "total"))).collectList();
        Mono<List<DayCount>> posted = stats.newAnnouncesPerDay(window).map(r -> new DayCount(str(r, "day"), num(r, "total"))).collectList();
        return Mono.zip(announces, listings, stats.memberCount().defaultIfEmpty(0L), stats.adminCount().defaultIfEmpty(0L),
                        stats.categoryCount().defaultIfEmpty(0L), stats.brandCount().defaultIfEmpty(0L), stats.listingClickTotal().defaultIfEmpty(0L), members)
                .zipWith(posted)
                .map(t -> new Dashboard(t.getT1().getT1(), t.getT1().getT2(), t.getT1().getT3(), t.getT1().getT4(), t.getT1().getT5(), t.getT1().getT6(),
                        t.getT1().getT7(), t.getT1().getT8(), t.getT2(), LocalDateTime.now()));
    }

    static String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) {
            v = row.get(key.toUpperCase());
        }
        return v == null ? null : String.valueOf(v);
    }

    static long num(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) {
            v = row.get(key.toUpperCase());
        }
        return v instanceof Number n ? n.longValue() : v == null ? 0L : Long.parseLong(String.valueOf(v));
    }
}
