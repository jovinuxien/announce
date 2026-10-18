package org.linlinjava.announce.listing.domain;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/** The {@code crawled_listing} table. */
public interface CrawledListingRepository {

    Mono<CrawledListing> findById(String id);

    /** Inserts or overwrites the content and quality columns; first_seen_at, click_count and the liveness columns of an existing row are kept. */
    Mono<Void> save(CrawledListing listing);

    /** The listing was seen again unchanged. */
    Mono<Void> touchSeen(String id, LocalDateTime seenAt);

    Mono<Void> updateStatus(String id, ListingStatus status, LocalDateTime now);

    /** Ids of ACTIVE rows whose last_seen_at is before {@code before}, oldest first. */
    Flux<String> staleIds(LocalDateTime before, int limit);

    /** ACTIVE rows in id order, one page. */
    Flux<CrawledListing> activePage(long offset, int limit);

    /** Adds one click; empty when the id is unknown. */
    Mono<CrawledListing> recordClick(String id, LocalDateTime now);

    /** ACTIVE rows least recently checked first, never-checked rows before all others. */
    Flux<CrawledListing> forLivenessCheck(int limit);

    /** Records a liveness check (url_checked_at, url_ok, image_ok). */
    Mono<Void> updateLiveness(String id, LocalDateTime checkedAt, Boolean urlOk, Boolean imageOk);

    /** Records a (re)computed grade and its issues. */
    Mono<Void> updateQuality(String id, ListingQuality quality, LocalDateTime now);

    /** Counts per source, status and grade for the inventory. */
    Flux<InventoryCount> inventoryCounts();
}
