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
import java.util.Locale;
import java.util.Map;

/**
 * The tradera.com feed of the scrapy spider {@code tradera_cars}, read from the page's {@code __NEXT_DATA__} items:
 * {@code itemId / description / itemType / startDate / endDate} (lists, as the ItemLoader emits them). The feed has
 * no url, title, price or image: the url is {@code https://www.tradera.com/item/{itemId}}, the title is the short
 * description, {@code itemType} containing "auction" gives sale type AUCTION with {@code ends_at} = endDate, anything
 * else FIXED_PRICE. Optional fields {@code title}, {@code itemUrl}, {@code price}, {@code imageUrl}, {@code category}
 * and {@code address} are used when a later spider version emits them.
 */
@Component
public class TraderaFeedParser implements ListingFeedParser {

    static final String ITEM_URL = "https://www.tradera.com/item/";

    private final JsonFeedReader reader;

    public TraderaFeedParser(ObjectMapper mapper) {
        this.reader = new JsonFeedReader(mapper);
    }

    @Override
    public ListingSource source() {
        return ListingSource.TRADERA;
    }

    @Override
    public Flux<CrawledItem> parse(Path file) {
        return reader.read(file).map(this::toItem);
    }

    CrawledItem toItem(JsonNode n) {
        String id = FeedFields.digits(FeedFields.text(n, "itemId"));
        if (id == null) {
            throw new IllegalArgumentException("tradera item without itemId");
        }
        String description = FeedFields.text(n, "description");
        String title = FeedFields.text(n, "title");
        String type = FeedFields.text(n, "itemType");
        boolean auction = type != null && type.toLowerCase(Locale.ROOT).contains("auction");
        String url = FeedFields.text(n, "itemUrl");
        return new CrawledItem(ListingSource.TRADERA, id, url == null ? ITEM_URL + id : DbaFeedParser.canonical(url), ListingKind.GOODS,
                title == null ? description : title, description, FeedFields.text(n, "imageUrl"),
                FeedFields.amount(FeedFields.text(n, "price")), ListingSource.TRADERA.currency(),
                auction ? ListingSaleType.AUCTION : ListingSaleType.FIXED_PRICE,
                auction ? FeedFields.dateTime(FeedFields.text(n, "endDate")) : null,
                FeedFields.text(n, "category"), Map.of(), FeedFields.text(n, "address"),
                FeedFields.dateTime(FeedFields.text(n, "startDate")));
    }
}
