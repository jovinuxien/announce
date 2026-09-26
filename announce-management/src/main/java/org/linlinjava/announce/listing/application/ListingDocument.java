package org.linlinjava.announce.listing.application;

import org.linlinjava.announce.listing.domain.CrawledListing;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The search document of a crawled listing, with the same field names as the announce documents so the facets
 * ({@code kind}, {@code sale_type}, {@code city}, {@code region_id}, {@code category_ids}, price range) and sorts
 * ({@code created_epoch}, {@code price}) span both origins. What tells them apart: {@code source} ({@code dba},
 * {@code tradera}), {@code external_url} (shown as a domain only; the click goes through {@code /out/{id}}) and
 * {@code source_category}. Attributes such as km and model year ride as dynamic facets like goods attributes.
 * Only ACTIVE listings are indexed, and they carry {@code status: PUBLISHED} so one status filter covers all hits.
 */
public record ListingDocument(String id, Map<String, Object> data) {

    public static ListingDocument of(CrawledListing l) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("announce_id", l.id());
        d.put("source", l.source().facetValue());
        d.put("external_url", l.url());
        d.put("kind", l.kind().name());
        d.put("title", l.title());
        d.put("description", l.brief());
        d.put("status", "PUBLISHED");
        put(d, "category_ids", l.categoryId() == null ? null : String.valueOf(l.categoryId()));
        put(d, "source_category", l.sourceCategory());
        put(d, "image_url", l.imageUrl());
        put(d, "price", l.price());
        put(d, "currency", l.currency());
        d.put("sale_type", l.saleType().name());
        put(d, "ends_epoch", epoch(l.endsAt()));
        d.put("created_epoch", epoch(l.publishedAt() == null ? l.firstSeenAt() : l.publishedAt()));
        put(d, "region_id", l.regionId() == null ? null : String.valueOf(l.regionId()));
        put(d, "city", l.address().city());
        put(d, "postal_code", l.address().postalCode());
        l.attributes().forEach((name, value) -> d.putIfAbsent(name, value));
        return new ListingDocument(l.id(), d);
    }

    private static Long epoch(LocalDateTime t) {
        return t == null ? null : t.toEpochSecond(ZoneOffset.UTC);
    }

    private static void put(Map<String, Object> d, String key, Object value) {
        if (value != null) {
            d.put(key, value);
        }
    }
}
