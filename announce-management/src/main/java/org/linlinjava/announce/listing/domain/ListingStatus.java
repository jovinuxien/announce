package org.linlinjava.announce.listing.domain;

/** ACTIVE while the crawler keeps seeing the listing; GONE once it has been missing for the grace period. */
public enum ListingStatus {
    ACTIVE, GONE
}
