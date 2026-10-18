package org.linlinjava.announce.listing.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * "Good candidate" as a grade computed from the listing's own fields plus the liveness verdict on its image.
 *
 * <p>Hard issues ({@code no_url}, {@code no_title}, {@code no_image}) make the grade {@link ListingGrade#REJECT}.
 * Soft issues make it {@link ListingGrade#WEAK}: {@code url_pattern} (not the canonical item url of the source),
 * {@code image_not_https}, {@code image_host_unknown}, {@code no_price} (fixed price without a price above zero),
 * {@code no_published_at}, {@code no_brief}, {@code no_attributes}, {@code image_dead} (the liveness check got no
 * image). No issue at all is {@link ListingGrade#GOOD}. The issue codes are stored next to the grade so the
 * inventory can say why a row is not GOOD.
 */
public record ListingQuality(ListingGrade grade, List<String> issues) {

    public static final String NO_URL = "no_url";
    public static final String NO_TITLE = "no_title";
    public static final String NO_IMAGE = "no_image";
    public static final String URL_PATTERN = "url_pattern";
    public static final String IMAGE_NOT_HTTPS = "image_not_https";
    public static final String IMAGE_HOST_UNKNOWN = "image_host_unknown";
    public static final String NO_PRICE = "no_price";
    public static final String NO_PUBLISHED_AT = "no_published_at";
    public static final String NO_BRIEF = "no_brief";
    public static final String NO_ATTRIBUTES = "no_attributes";
    public static final String IMAGE_DEAD = "image_dead";

    private static final Pattern HTTPS_HOST = Pattern.compile("^https://([^/:?#]+)", Pattern.CASE_INSENSITIVE);

    public ListingQuality {
        issues = List.copyOf(issues);
    }

    /** The grade of a freshly parsed item; the image has not been checked yet. */
    public static ListingQuality of(CrawledItem item) {
        return assess(item.source(), item.url(), item.title(), item.imageUrl(), item.price(), item.saleType(),
                item.publishedAt(), item.brief(), item.attributes(), null);
    }

    /** The grade of a stored listing, taking the liveness verdict on its image into account. */
    public static ListingQuality of(CrawledListing l) {
        return assess(l.source(), l.url(), l.title(), l.imageUrl(), l.price(), l.saleType(), l.publishedAt(), l.brief(),
                l.attributes(), l.imageOk());
    }

    static ListingQuality assess(ListingSource source, String url, String title, String imageUrl, BigDecimal price,
                                 ListingSaleType saleType, LocalDateTime publishedAt, String brief,
                                 Map<String, String> attributes, Boolean imageOk) {
        List<String> hard = new ArrayList<>();
        List<String> soft = new ArrayList<>();
        if (blank(url)) {
            hard.add(NO_URL);
        } else if (!source.isItemUrl(url)) {
            soft.add(URL_PATTERN);
        }
        if (blank(title)) {
            hard.add(NO_TITLE);
        }
        if (blank(imageUrl)) {
            hard.add(NO_IMAGE);
        } else {
            var m = HTTPS_HOST.matcher(imageUrl.strip());
            if (!m.find()) {
                soft.add(IMAGE_NOT_HTTPS);
            } else if (!source.isImageHost(m.group(1).toLowerCase(Locale.ROOT))) {
                soft.add(IMAGE_HOST_UNKNOWN);
            }
            if (Boolean.FALSE.equals(imageOk)) {
                soft.add(IMAGE_DEAD);
            }
        }
        if (saleType != ListingSaleType.AUCTION && (price == null || price.signum() <= 0)) {
            soft.add(NO_PRICE);
        }
        if (publishedAt == null) {
            soft.add(NO_PUBLISHED_AT);
        }
        if (blank(brief)) {
            soft.add(NO_BRIEF);
        }
        if (attributes == null || attributes.isEmpty()) {
            soft.add(NO_ATTRIBUTES);
        }
        if (!hard.isEmpty()) {
            hard.addAll(soft);
            return new ListingQuality(ListingGrade.REJECT, hard);
        }
        return new ListingQuality(soft.isEmpty() ? ListingGrade.GOOD : ListingGrade.WEAK, soft);
    }

    public boolean is(ListingGrade g) {
        return grade == g;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
