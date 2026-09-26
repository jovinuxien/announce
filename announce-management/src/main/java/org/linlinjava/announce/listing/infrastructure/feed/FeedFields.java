package org.linlinjava.announce.listing.infrastructure.feed;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Reading scrapy item fields: an {@code ItemLoader} field is a list (usually one element), values carry the page's
 * whitespace ({@code "\r\n    6580 Vamdrup\r\n"}). Every accessor answers null for a missing or blank field.
 */
final class FeedFields {

    private static final DateTimeFormatter SPACE_SEPARATED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSSSSS][.SSS]");

    private FeedFields() {
    }

    /** The first element of the field as trimmed text (or the scalar itself). */
    static String text(JsonNode item, String name) {
        return text(item, name, 0);
    }

    static String text(JsonNode item, String name, int index) {
        JsonNode f = item.get(name);
        if (f == null || f.isNull()) {
            return null;
        }
        JsonNode v = f.isArray() ? (f.size() > index ? f.get(index) : null) : (index == 0 ? f : null);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText().strip();
        return s.isEmpty() ? null : s;
    }

    /** Number of elements in a list field (1 for a scalar, 0 when missing). */
    static int size(JsonNode item, String name) {
        JsonNode f = item.get(name);
        return f == null || f.isNull() ? 0 : f.isArray() ? f.size() : 1;
    }

    /** "22900", "22.900", "22 900 kr", "1.234,50" -> a decimal; null when nothing numeric is left. */
    static BigDecimal amount(String s) {
        if (s == null) {
            return null;
        }
        String digits = s.replaceAll("[^0-9,.]", "");
        if (digits.isEmpty()) {
            return null;
        }
        int comma = digits.lastIndexOf(','), dot = digits.lastIndexOf('.');
        String normalised;
        if (comma > dot) {                                     // 1.234,50 -> decimal comma
            normalised = digits.replace(".", "").replace(',', '.');
        } else if (dot > comma && digits.length() - dot - 1 == 3 && dot > 0) {   // 273.000 -> thousands separator
            normalised = digits.replace(".", "").replace(",", "");
        } else {
            normalised = digits.replace(",", "");
        }
        try {
            return new BigDecimal(normalised);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Digits only ("273.000" -> "273000"), for attribute values used as numeric facets. */
    static String digits(String s) {
        if (s == null) {
            return null;
        }
        String d = s.replaceAll("[^0-9]", "");
        return d.isEmpty() ? null : d;
    }

    /** ISO-8601 with or without offset, or the scrapy {@code datetime} text "2024-10-07 12:27:17.640517"; UTC. */
    static LocalDateTime dateTime(String s) {
        if (s == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(s).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(s), ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(s);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(s, SPACE_SEPARATED);
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }
}
