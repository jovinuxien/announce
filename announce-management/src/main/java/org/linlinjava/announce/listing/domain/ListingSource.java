package org.linlinjava.announce.listing.domain;

import java.util.Locale;

/** Where a crawled listing comes from. The facet value is the lower-case name; {@code local} is reserved for announces. */
public enum ListingSource {
    DBA("dba.dk", "DKK"),
    TRADERA("tradera.com", "SEK");

    private final String domain;
    private final String currency;

    ListingSource(String domain, String currency) {
        this.domain = domain;
        this.currency = currency;
    }

    /** Value of the {@code source} facet in the search index and prefix of every listing id. */
    public String facetValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The site, for display next to a hit. */
    public String domain() {
        return domain;
    }

    /** The currency prices on the site are quoted in. */
    public String currency() {
        return currency;
    }

    /** Source of a listing id ({@code dba-1117479380}) or a feed file name ({@code dba_202410081230.jsonl}). */
    public static ListingSource ofPrefix(String name) {
        String head = name.split("[-_]", 2)[0];
        return valueOf(head.toUpperCase(Locale.ROOT));
    }
}
