package org.linlinjava.announce.listing.domain;

/** Quality grade of a crawled listing (see {@link ListingQuality}). Only GOOD and WEAK are indexed by default. */
public enum ListingGrade {
    /** Everything a hit needs: title, image on a known host, canonical url, price (or an auction), published date. */
    GOOD,
    /** Usable but a soft field is missing or doubtful (published date, brief, attributes, url pattern, image host, dead image). */
    WEAK,
    /** Not usable as a hit: url, title or image missing. Kept in the table with its issues, never indexed. */
    REJECT
}
