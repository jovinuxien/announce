package org.linlinjava.announce.listing.application;

/** The listing existed but the source no longer shows it: HTTP 410. */
public class ListingGoneException extends RuntimeException {
    public ListingGoneException(String id) {
        super("crawled listing " + id + " is gone from its source");
    }
}
