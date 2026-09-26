package org.linlinjava.announce.listing.application;

public class ListingNotFoundException extends RuntimeException {
    public ListingNotFoundException(String id) {
        super("crawled listing " + id + " not found");
    }
}
