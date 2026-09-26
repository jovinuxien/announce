package org.linlinjava.announce.listing.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.StringJoiner;

/**
 * SHA-256 over what the search document shows: title, brief, price, sale type, auction end, attributes, image and
 * address. A listing whose hash is unchanged between two runs is only touched, not re-indexed.
 */
public final class ContentHash {

    private ContentHash() {
    }

    public static String of(CrawledItem item) {
        StringJoiner j = new StringJoiner("\u001f");
        j.add(item.title());
        j.add(item.brief());
        j.add(item.price() == null ? "" : item.price().stripTrailingZeros().toPlainString());
        j.add(item.saleType().name());
        j.add(item.endsAt() == null ? "" : item.endsAt().toString());
        for (Map.Entry<String, String> e : item.attributes().entrySet()) {   // sorted: CrawledItem keeps a TreeMap
            j.add(e.getKey() + "=" + e.getValue());
        }
        j.add(item.imageUrl() == null ? "" : item.imageUrl());
        j.add(item.addressRaw() == null ? "" : item.addressRaw());
        return sha256(j.toString());
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
