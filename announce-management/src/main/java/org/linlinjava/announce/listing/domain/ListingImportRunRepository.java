package org.linlinjava.announce.listing.domain;

import reactor.core.publisher.Mono;

/** The {@code listing_import_run} table: one row per import, the latest per source for the inventory. */
public interface ListingImportRunRepository {

    Mono<Void> record(ListingImportRun run);

    /** The most recently finished run of a source; empty when none was recorded. */
    Mono<ListingImportRun> latest(ListingSource source);
}
