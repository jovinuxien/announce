package org.linlinjava.announce.listing.infrastructure.persistence.mybatis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.announce.db.domain.CrawledListing;
import org.linlinjava.announce.listing.domain.Address;
import org.linlinjava.announce.listing.domain.ListingKind;
import org.linlinjava.announce.listing.domain.ListingSaleType;
import org.linlinjava.announce.listing.domain.ListingSource;
import org.linlinjava.announce.listing.domain.ListingStatus;

import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;

/** Conversions between the {@code crawled_listing} row and the domain record; attributes travel as a JSON object. */
final class ListingRows {

    private static final TypeReference<TreeMap<String, String>> ATTRIBUTES = new TypeReference<>() {
    };

    private final ObjectMapper json;

    ListingRows(ObjectMapper json) {
        this.json = json;
    }

    org.linlinjava.announce.listing.domain.CrawledListing toDomain(CrawledListing r) {
        return new org.linlinjava.announce.listing.domain.CrawledListing(r.getId(), ListingSource.valueOf(r.getSource()),
                r.getExternalId(), r.getUrl(), r.getContentHash(), ListingKind.valueOf(r.getKind()), r.getTitle(), r.getBrief(),
                r.getImageUrl(), r.getPrice(), r.getCurrency(), ListingSaleType.valueOf(r.getSaleType()), r.getEndsAt(),
                r.getSourceCategory(), r.getCategoryId(), attributes(r.getAttributes()),
                new Address(r.getAddressRaw(), r.getPostalCode(), r.getCity()), r.getRegionId(), r.getPublishedAt(),
                r.getFirstSeenAt(), r.getLastSeenAt(), ListingStatus.valueOf(r.getStatus()),
                r.getClickCount() == null ? 0 : r.getClickCount());
    }

    CrawledListing toRow(org.linlinjava.announce.listing.domain.CrawledListing l, LocalDateTime now) {
        CrawledListing r = new CrawledListing();
        r.setId(l.id());
        r.setSource(l.source().name());
        r.setExternalId(l.externalId());
        r.setUrl(l.url());
        r.setContentHash(l.contentHash());
        r.setKind(l.kind().name());
        r.setTitle(l.title());
        r.setBrief(l.brief());
        r.setImageUrl(l.imageUrl());
        r.setPrice(l.price());
        r.setCurrency(l.currency());
        r.setSaleType(l.saleType().name());
        r.setEndsAt(l.endsAt());
        r.setSourceCategory(l.sourceCategory());
        r.setCategoryId(l.categoryId());
        r.setAttributes(l.attributes().isEmpty() ? null : write(l.attributes()));
        r.setAddressRaw(l.address().raw());
        r.setRegionId(l.regionId());
        r.setCity(l.address().city());
        r.setPostalCode(l.address().postalCode());
        r.setPublishedAt(l.publishedAt());
        r.setFirstSeenAt(l.firstSeenAt());
        r.setLastSeenAt(l.lastSeenAt());
        r.setStatus(l.status().name());
        r.setClickCount(l.clickCount());
        r.setAddTime(now);
        r.setUpdateTime(now);
        return r;
    }

    private Map<String, String> attributes(String s) {
        if (s == null || s.isBlank()) {
            return Map.of();
        }
        try {
            return Map.copyOf(json.readValue(s, ATTRIBUTES));
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String write(Map<String, String> attributes) {
        try {
            return json.writeValueAsString(new TreeMap<>(attributes));
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
