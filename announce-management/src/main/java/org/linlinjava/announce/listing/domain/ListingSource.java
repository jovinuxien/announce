package org.linlinjava.announce.listing.domain;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Where a crawled listing comes from. The facet value is the lower-case name; {@code local} is reserved for announces. */
public enum ListingSource {
    /** Item urls: {@code https://www.dba.dk/mobility/item/25925994} (2026) or {@code https://www.dba.dk/<slug>/id-1117479380/} (legacy). */
    DBA("dba.dk", "DKK", "^https://www\\.dba\\.dk/(mobility/item/\\d+|[^?#]*/id-\\d+/?)$",
            Set.of("images.dbastatic.dk", "billeder.dba.dk")),
    /** Item urls: {@code https://www.tradera.com/item/1001/756274596/<slug>} or the short {@code /item/756274596}. */
    TRADERA("tradera.com", "SEK", "^https://www\\.tradera\\.com/item/\\d+(/\\d+(/[^/?#]*)?)?/?$",
            Set.of("img.tradera.net"));

    private final String domain;
    private final String currency;
    private final Pattern itemUrl;
    private final Set<String> imageHosts;

    ListingSource(String domain, String currency, String itemUrl, Set<String> imageHosts) {
        this.domain = domain;
        this.currency = currency;
        this.itemUrl = Pattern.compile(itemUrl);
        this.imageHosts = imageHosts;
    }

    /** True when the url is a canonical item page of this source (the click-out target the quality rules expect). */
    public boolean isItemUrl(String url) {
        return url != null && itemUrl.matcher(url.strip()).matches();
    }

    /** True for the image hosts the site serves listing pictures from. */
    public boolean isImageHost(String host) {
        return host != null && imageHosts.contains(host.toLowerCase(Locale.ROOT));
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
