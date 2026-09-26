package org.linlinjava.announce.listing.domain;

import reactor.core.publisher.Mono;

/** {@code listing_category_map}: the site's category path -> {@code announce_category} of kind GOODS. Data, not code. */
public interface ListingCategoryMapping {

    /** Empty when the path is not mapped. */
    Mono<Integer> categoryFor(ListingSource source, String sourceCategory);
}
