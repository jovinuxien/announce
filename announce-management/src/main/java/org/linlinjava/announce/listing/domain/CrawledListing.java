package org.linlinjava.announce.listing.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * A listing crawled from another site, as this service keeps it: the snippet and link-out data of a
 * {@link CrawledItem}, the derived location and category, and the lifecycle the crawler drives. It is never an
 * announce: it has no owner here, enters no event store, and a click on it leaves for {@link #url()}.
 *
 * <p>Lifecycle: ACTIVE while the feed keeps seeing it; GONE once {@link #isStale} for the grace period; back to
 * ACTIVE (and re-indexed) when a later feed shows it again.
 */
public record CrawledListing(String id,
                             ListingSource source,
                             String externalId,
                             String url,
                             String contentHash,
                             ListingKind kind,
                             String title,
                             String brief,
                             String imageUrl,
                             BigDecimal price,
                             String currency,
                             ListingSaleType saleType,
                             LocalDateTime endsAt,
                             String sourceCategory,
                             Integer categoryId,
                             Map<String, String> attributes,
                             Address address,
                             Integer regionId,
                             LocalDateTime publishedAt,
                             LocalDateTime firstSeenAt,
                             LocalDateTime lastSeenAt,
                             ListingStatus status,
                             int clickCount) {

    /** A listing seen for the first time at {@code now}. */
    public static CrawledListing firstSeen(CrawledItem item, Integer regionId, Integer categoryId, LocalDateTime now) {
        return new CrawledListing(item.id(), item.source(), item.externalId(), item.url(), ContentHash.of(item), item.kind(),
                item.title(), item.brief(), item.imageUrl(), item.price(), item.currency(), item.saleType(), item.endsAt(),
                item.sourceCategory(), categoryId, item.attributes(), Address.parse(item.addressRaw()), regionId,
                item.publishedAt(), now, now, ListingStatus.ACTIVE, 0);
    }

    /** The same listing seen again with (possibly) new content: first seen and clicks are kept, status is ACTIVE. */
    public CrawledListing seenAgain(CrawledItem item, Integer regionId, Integer categoryId, LocalDateTime now) {
        return new CrawledListing(id, source, externalId, item.url(), ContentHash.of(item), item.kind(),
                item.title(), item.brief(), item.imageUrl(), item.price(), item.currency(), item.saleType(), item.endsAt(),
                item.sourceCategory(), categoryId, item.attributes(), Address.parse(item.addressRaw()), regionId,
                item.publishedAt(), firstSeenAt, now, ListingStatus.ACTIVE, clickCount);
    }

    /** True when the feed content differs from what this row holds. */
    public boolean contentChanged(CrawledItem item) {
        return !contentHash.equals(ContentHash.of(item));
    }

    public boolean isActive() {
        return status == ListingStatus.ACTIVE;
    }

    /** Not seen for longer than the grace period. */
    public boolean isStale(LocalDateTime now, Duration goneAfter) {
        return lastSeenAt.isBefore(now.minus(goneAfter));
    }

    public CrawledListing gone() {
        return withStatus(ListingStatus.GONE);
    }

    private CrawledListing withStatus(ListingStatus s) {
        return new CrawledListing(id, source, externalId, url, contentHash, kind, title, brief, imageUrl, price, currency,
                saleType, endsAt, sourceCategory, categoryId, attributes, address, regionId, publishedAt, firstSeenAt,
                lastSeenAt, s, clickCount);
    }
}
