package org.linlinjava.announce.listing.infrastructure.feed;

import com.fasterxml.jackson.databind.JsonNode;
import org.linlinjava.announce.listing.domain.CrawledItem;
import org.linlinjava.announce.listing.domain.ListingKind;
import org.linlinjava.announce.listing.domain.ListingSaleType;
import org.linlinjava.announce.listing.domain.ListingSource;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The importer's own feed shape, {@code "schema": "crawled-item/2"}: one flat JSON object per line with the fields of
 * {@link CrawledItem} ({@code externalId, url, title, brief, imageUrl, price, currency, saleType, endsAt,
 * sourceCategory, attributes{}, addressRaw, publishedAt}) plus {@code crawledAt} and an optional {@code source} and
 * {@code kind}. The spiders in {@code crawler/} emit it for every source; the per-source parsers delegate here when a
 * line carries the schema marker, so a legacy file and a v2 file import through the same path.
 */
public final class CrawledItemV2Parser {

    public static final String SCHEMA = "crawled-item/2";

    private CrawledItemV2Parser() {
    }

    /** True when the object is a v2 line (any {@code schema} value starting with {@code crawled-item/2}). */
    public static boolean isV2(JsonNode n) {
        JsonNode s = n.get("schema");
        return s != null && s.isTextual() && s.asText().startsWith(SCHEMA);
    }

    /** The item of a v2 line; {@code source} is the file's source and must match the line's own {@code source} when present. */
    public static CrawledItem toItem(ListingSource source, JsonNode n) {
        String declared = FeedFields.text(n, "source");
        if (declared != null && !declared.equalsIgnoreCase(source.name())) {
            throw new IllegalArgumentException("line of source " + declared + " in a " + source + " feed");
        }
        String url = FeedFields.text(n, "url");
        String externalId = FeedFields.text(n, "externalId");
        if (url == null || externalId == null) {
            throw new IllegalArgumentException("v2 item without url or externalId");
        }
        String saleType = FeedFields.text(n, "saleType");
        ListingSaleType sale = saleType == null ? ListingSaleType.FIXED_PRICE : ListingSaleType.valueOf(saleType.toUpperCase(Locale.ROOT));
        String kind = FeedFields.text(n, "kind");
        String currency = FeedFields.text(n, "currency");
        return new CrawledItem(source, externalId, DbaFeedParser.canonical(url),
                kind == null ? ListingKind.GOODS : ListingKind.valueOf(kind.toUpperCase(Locale.ROOT)),
                FeedFields.text(n, "title"), FeedFields.text(n, "brief"), FeedFields.text(n, "imageUrl"),
                FeedFields.amount(FeedFields.text(n, "price")), currency == null ? source.currency() : currency.toUpperCase(Locale.ROOT),
                sale, FeedFields.dateTime(FeedFields.text(n, "endsAt")), FeedFields.text(n, "sourceCategory"),
                attributes(n.get("attributes")), FeedFields.text(n, "addressRaw"), FeedFields.dateTime(FeedFields.text(n, "publishedAt")));
    }

    private static Map<String, String> attributes(JsonNode a) {
        Map<String, String> m = new LinkedHashMap<>();
        if (a == null || !a.isObject()) {
            return m;
        }
        for (Iterator<Map.Entry<String, JsonNode>> it = a.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            if (!e.getValue().isNull()) {
                String v = e.getValue().asText().strip();
                if (!v.isEmpty()) {
                    m.put(e.getKey(), v);
                }
            }
        }
        return m;
    }
}
