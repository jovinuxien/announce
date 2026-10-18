package org.linlinjava.announce.listing.infrastructure.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.announce.listing.domain.CrawledItem;
import org.linlinjava.announce.listing.domain.ListingFeedParser;
import org.linlinjava.announce.listing.domain.ListingKind;
import org.linlinjava.announce.listing.domain.ListingSaleType;
import org.linlinjava.announce.listing.domain.ListingSource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The dba.dk car feed of the scrapy spider {@code dba_cars}: {@code itemName / itemDescription / itemUrl / itemImage /
 * itemPris / itemCategory / itemSubCategory / itemKm / itemModelYear / itemAddress / itemPublishedDate}, each a list
 * with the page's whitespace. The external id is the {@code id-<digits>} segment of the url; the price is in DKK;
 * {@code itemAddress} has a second element with the dealer's name for trade sellers; km and model year become the
 * attributes {@code km} and {@code model_year}; the site's category path is {@code itemCategory/itemSubCategory}.
 * Lines carrying {@code "schema": "crawled-item/2"} (the spiders in {@code crawler/}) go through {@link CrawledItemV2Parser}.
 */
@Component
public class DbaFeedParser implements ListingFeedParser {

    static final Pattern EXTERNAL_ID = Pattern.compile("/id-(\\d+)/?");

    private final JsonFeedReader reader;

    public DbaFeedParser(ObjectMapper mapper) {
        this.reader = new JsonFeedReader(mapper);
    }

    @Override
    public ListingSource source() {
        return ListingSource.DBA;
    }

    @Override
    public Flux<CrawledItem> parse(Path file) {
        return reader.read(file).map(n -> CrawledItemV2Parser.isV2(n) ? CrawledItemV2Parser.toItem(ListingSource.DBA, n) : toItem(n));
    }

    CrawledItem toItem(JsonNode n) {
        String url = FeedFields.text(n, "itemUrl");
        if (url == null) {
            throw new IllegalArgumentException("dba item without itemUrl");
        }
        Matcher m = EXTERNAL_ID.matcher(url);
        if (!m.find()) {
            throw new IllegalArgumentException("dba url without id segment: " + url);
        }
        String category = FeedFields.text(n, "itemCategory");
        String sub = FeedFields.text(n, "itemSubCategory");
        String path = category == null ? sub : sub == null ? category : category + "/" + sub;
        Map<String, String> attributes = new LinkedHashMap<>();
        put(attributes, "km", FeedFields.digits(FeedFields.text(n, "itemKm")));
        put(attributes, "model_year", FeedFields.digits(FeedFields.text(n, "itemModelYear")));
        put(attributes, "dealer", FeedFields.size(n, "itemAddress") > 1 ? FeedFields.text(n, "itemAddress", 1) : null);
        return new CrawledItem(ListingSource.DBA, m.group(1), canonical(url), ListingKind.GOODS,
                FeedFields.text(n, "itemName"), FeedFields.text(n, "itemDescription"), FeedFields.text(n, "itemImage"),
                FeedFields.amount(FeedFields.text(n, "itemPris")), ListingSource.DBA.currency(), ListingSaleType.FIXED_PRICE, null,
                path, attributes, FeedFields.text(n, "itemAddress"), FeedFields.dateTime(FeedFields.text(n, "itemPublishedDate")));
    }

    /** The listing page without query string or fragment. */
    static String canonical(String url) {
        int cut = url.indexOf('?');
        if (cut < 0) {
            cut = url.indexOf('#');
        }
        return cut < 0 ? url : url.substring(0, cut);
    }

    private static void put(Map<String, String> m, String k, String v) {
        if (v != null) {
            m.put(k, v);
        }
    }
}
