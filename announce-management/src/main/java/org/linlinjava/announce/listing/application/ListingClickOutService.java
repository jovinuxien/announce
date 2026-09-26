package org.linlinjava.announce.listing.application;

import org.linlinjava.announce.listing.domain.CrawledListing;
import org.linlinjava.announce.listing.domain.CrawledListingRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDateTime;

/** Resolves {@code /out/{id}}: counts the click and answers with the listing to redirect to. */
@Service
public class ListingClickOutService {

    private final CrawledListingRepository listings;
    private final Clock clock;

    public ListingClickOutService(CrawledListingRepository listings, Clock clock) {
        this.listings = listings;
        this.clock = clock;
    }

    /** The ACTIVE listing, its click counted; {@link ListingNotFoundException} or {@link ListingGoneException} otherwise. */
    public Mono<CrawledListing> clickOut(String id) {
        return listings.findById(id)
                .switchIfEmpty(Mono.error(() -> new ListingNotFoundException(id)))
                .flatMap(l -> l.isActive() ? listings.recordClick(id, LocalDateTime.now(clock)).defaultIfEmpty(l)
                        : Mono.error(new ListingGoneException(id)));
    }
}
