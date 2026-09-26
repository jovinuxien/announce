package org.linlinjava.announce.listing.domain;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/** The {@code crawled_listing} table. */
public interface CrawledListingRepository {

    Mono<CrawledListing> findById(String id);

    /** Inserts or overwrites the content columns; first_seen_at and click_count of an existing row are kept. */
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
}
