package org.linlinjava.announce.listing.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The address text a site shows, split into what the search facets need. {@code "6580 Vamdrup"} gives postal code
 * {@code 6580} and city {@code Vamdrup}; text without a leading postal code is kept as the city. No geocoding.
 */
public record Address(String raw, String postalCode, String city) {

    private static final Pattern POSTAL_THEN_CITY = Pattern.compile("^(\\d{3,5})\\s+(.+)$");
    private static final Pattern CITY_THEN_POSTAL = Pattern.compile("^(.+?)\\s+(\\d{3,5})$");

    public static final Address NONE = new Address(null, null, null);

    public static Address parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        String text = raw.strip().replaceAll("\\s+", " ");
        Matcher m = POSTAL_THEN_CITY.matcher(text);
        if (m.matches()) {
            return new Address(text, m.group(1), m.group(2).strip());
        }
        m = CITY_THEN_POSTAL.matcher(text);
        if (m.matches()) {
            return new Address(text, m.group(2), m.group(1).strip());
        }
        return new Address(text, null, text);
    }
}
