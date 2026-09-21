package org.linlinjava.announce.goods.domain.announce;

import java.math.BigDecimal;

/**
 * Where the item is, Blocket / DBA style: a region (announce_region.id, e.g. the municipality),
 * the city and postal code the member typed, and optional coordinates for distance search.
 * At least one of region, city or postal code is required.
 */
public record Location(Integer regionId, String city, String postalCode, BigDecimal latitude, BigDecimal longitude) {

    public Location {
        city = blankToNull(city);
        postalCode = blankToNull(postalCode);
        if (regionId == null && city == null && postalCode == null) {
            throw new IllegalArgumentException("A location needs at least a region, a city or a postal code");
        }
        if (regionId != null && regionId <= 0) {
            throw new IllegalArgumentException("Region id must be positive");
        }
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("Latitude and longitude must be given together");
        }
        if (latitude != null && (latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0)) {
            throw new IllegalArgumentException("Coordinates out of range");
        }
    }

    public static Location inRegion(Integer regionId, String city, String postalCode) {
        return new Location(regionId, city, postalCode, null, null);
    }

    public boolean hasCoordinates() {
        return latitude != null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
