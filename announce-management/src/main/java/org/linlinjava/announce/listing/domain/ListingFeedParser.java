package org.linlinjava.announce.listing.domain;

import reactor.core.publisher.Flux;

import java.nio.file.Path;

/** Turns one feed file of a source (the scrapy output) into normalised items. One implementation per source. */
public interface ListingFeedParser {

    ListingSource source();

    Flux<CrawledItem> parse(Path file);
}
