package org.linlinjava.announce.listing.infrastructure.feed;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.listing.domain.CrawledItem;
import org.linlinjava.announce.listing.domain.ListingSaleType;
import org.linlinjava.announce.listing.domain.ListingSource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The two feed parsers over the fixtures (dba: indented array as scrapy's json feed; tradera: json lines). */
class FeedParsersTest {

    static final Path FIXTURES = Path.of("src/test/resources/listings");
    final ObjectMapper mapper = new ObjectMapper();

    @Test
    void dbaArrayFeedMapsEveryFieldAndStripsThePadding() {
        List<CrawledItem> items = new DbaFeedParser(mapper).parse(FIXTURES.resolve("dba_fixture.json")).collectList().block();
        assertThat(items).hasSize(3);
        CrawledItem aygo = items.get(0);
        assertThat(aygo.source()).isEqualTo(ListingSource.DBA);
        assertThat(aygo.id()).isEqualTo("dba-1117479380");
        assertThat(aygo.url()).isEqualTo("https://www.dba.dk/toyota-aygo/id-1117479380/");
        assertThat(aygo.title()).isEqualTo("Toyota Aygo, 1,0, Benzin, 2007, km 273000...");
        assertThat(aygo.brief()).startsWith("Toyota Aygo, 1,0, Benzin, 2007, km 273000, turkismetal");
        assertThat(aygo.imageUrl()).endsWith(".jpeg?class=S300X300");
        assertThat(aygo.price()).isEqualByComparingTo(new BigDecimal("22900"));
        assertThat(aygo.currency()).isEqualTo("DKK");
        assertThat(aygo.saleType()).isEqualTo(ListingSaleType.FIXED_PRICE);
        assertThat(aygo.sourceCategory()).isEqualTo("Cars/Private Car");
        assertThat(aygo.attributes()).containsExactlyInAnyOrderEntriesOf(Map.of("km", "273000", "model_year", "2007"));
        assertThat(aygo.addressRaw()).isEqualTo("6580 Vamdrup");
        assertThat(aygo.publishedAt()).isEqualTo(LocalDateTime.of(2024, 10, 7, 12, 27, 17, 640517000));

        CrawledItem dealerCar = items.get(1);
        assertThat(dealerCar.attributes()).containsEntry("dealer", "Brdr. Møll A/S");
        assertThat(dealerCar.addressRaw()).isEqualTo("9800 Hjørring");
        assertThat(items.get(2).brief()).hasSize(CrawledItem.BRIEF_LENGTH);
    }

    @Test
    void traderaJsonLinesFeedDerivesUrlTitleAndAuctionEnd() {
        List<CrawledItem> items = new TraderaFeedParser(mapper).parse(FIXTURES.resolve("tradera_fixture.jsonl")).collectList().block();
        assertThat(items).hasSize(2);
        CrawledItem auction = items.get(0);
        assertThat(auction.id()).isEqualTo("tradera-640012345");
        assertThat(auction.url()).isEqualTo("https://www.tradera.com/item/640012345");
        assertThat(auction.title()).isEqualTo("Volvo V70 2.4 D5 2008, besiktad, nyservad");
        assertThat(auction.saleType()).isEqualTo(ListingSaleType.AUCTION);
        assertThat(auction.endsAt()).isEqualTo(LocalDateTime.of(2024, 10, 15, 18, 30));
        assertThat(auction.price()).isNull();
        assertThat(auction.currency()).isEqualTo("SEK");
        assertThat(auction.publishedAt()).isEqualTo(LocalDateTime.of(2024, 10, 8, 10, 0));
        CrawledItem fixed = items.get(1);
        assertThat(fixed.saleType()).isEqualTo(ListingSaleType.FIXED_PRICE);
        assertThat(fixed.endsAt()).isNull();
    }

    @Test
    void brokenFeedFailsTheStreamAfterTheGoodItems() {
        assertThat(new TraderaFeedParser(mapper).parse(FIXTURES.resolve("tradera_broken.jsonl")).map(CrawledItem::id)
                .onErrorResume(e -> reactor.core.publisher.Mono.just("error:" + e.getClass().getSimpleName())).collectList().block())
                .containsExactly("tradera-1", "error:UncheckedIOException");
    }

    @Test
    void amountsAndDates() {
        assertThat(FeedFields.amount("22900")).isEqualByComparingTo("22900");
        assertThat(FeedFields.amount("273.000")).isEqualByComparingTo("273000");
        assertThat(FeedFields.amount("1.234,50")).isEqualByComparingTo("1234.50");
        assertThat(FeedFields.amount("22 900 kr")).isEqualByComparingTo("22900");
        assertThat(FeedFields.amount("12.5")).isEqualByComparingTo("12.5");
        assertThat(FeedFields.amount("Ring")).isNull();
        assertThat(FeedFields.digits("\r\n 273.000 \r\n")).isEqualTo("273000");
        assertThat(FeedFields.dateTime("2024-10-07 12:27:17.640517")).isEqualTo(LocalDateTime.of(2024, 10, 7, 12, 27, 17, 640517000));
        assertThat(FeedFields.dateTime("2024-10-08T10:00:00+02:00")).isEqualTo(LocalDateTime.of(2024, 10, 8, 8, 0));
        assertThat(FeedFields.dateTime("2024-10-08T10:00:00")).isEqualTo(LocalDateTime.of(2024, 10, 8, 10, 0));
        assertThat(FeedFields.dateTime("yesterday")).isNull();
    }

    @Test
    void v2LinesOfTheRepoSpidersGoThroughTheGenericParser() {
        List<CrawledItem> items = new DbaFeedParser(mapper).parse(FIXTURES.resolve("dba_v2_fixture.jsonl")).collectList().block();
        assertThat(items).hasSize(2);
        CrawledItem peugeot = items.get(0);
        assertThat(peugeot.id()).isEqualTo("dba-25925994");
        assertThat(peugeot.url()).as("query string stripped").isEqualTo("https://www.dba.dk/mobility/item/25925994");
        assertThat(peugeot.title()).isEqualTo("Peugeot 308");
        assertThat(peugeot.price()).isEqualByComparingTo("46000");
        assertThat(peugeot.currency()).isEqualTo("DKK");
        assertThat(peugeot.saleType()).isEqualTo(ListingSaleType.FIXED_PRICE);
        assertThat(peugeot.endsAt()).isNull();
        assertThat(peugeot.attributes()).containsEntry("km", "223000").containsEntry("model_year", "2015").containsEntry("fuel", "Diesel");
        assertThat(peugeot.addressRaw()).isEqualTo("Lystrup");
        assertThat(peugeot.publishedAt()).isEqualTo(LocalDateTime.of(2026, 10, 17, 17, 56));
        assertThat(items.get(1).imageUrl()).isNull();
        assertThat(items.get(1).attributes()).containsOnlyKeys("km", "model_year");
    }

    @Test
    void aV2LineOfAnotherSourceIsRefused() {
        com.fasterxml.jackson.databind.JsonNode line = mapper.createObjectNode().put("schema", "crawled-item/2").put("source", "TRADERA")
                .put("externalId", "1").put("url", "https://www.tradera.com/item/1").put("title", "x");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> CrawledItemV2Parser.toItem(ListingSource.DBA, line))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("TRADERA");
    }
}
