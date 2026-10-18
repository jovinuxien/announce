package org.linlinjava.announce.listing.domain;

import java.time.LocalDateTime;

/** One import of a crawled feed, as the inventory reports it: which file, when, and the counts of the run. */
public record ListingImportRun(ListingSource source,
                               String file,
                               LocalDateTime startedAt,
                               LocalDateTime finishedAt,
                               long seen,
                               long inserted,
                               long updated,
                               long unchanged,
                               long rejected,
                               long good,
                               long weak,
                               long reject) {
}
