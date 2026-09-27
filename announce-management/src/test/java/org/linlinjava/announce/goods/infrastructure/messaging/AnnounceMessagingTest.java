package org.linlinjava.announce.goods.infrastructure.messaging;

import org.junit.jupiter.api.Test;
import org.linlinjava.announce.cqrs.readside.EventEnvelope;
import org.linlinjava.announce.goods.api.AnnouncePublishedV1;
import org.linlinjava.announce.goods.domain.announce.AnnounceContent;
import org.linlinjava.announce.goods.domain.announce.Condition;
import org.linlinjava.announce.goods.domain.announce.GoodsDetails;
import org.linlinjava.announce.goods.domain.announce.Location;
import org.linlinjava.announce.goods.domain.announce.Money;
import org.linlinjava.announce.goods.domain.announce.SaleType;
import org.linlinjava.announce.goods.domain.announce.SellerId;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceEdited;
import org.linlinjava.announce.goods.domain.announce.events.AnnouncePublished;
import org.linlinjava.announce.goods.domain.category.CategoryId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The relay mappers: a current event becomes its public record, an event from before the public topic is skipped. */
class AnnounceMessagingTest {

    private static final EventEnvelope ENVELOPE = new EventEnvelope("/announce/abc", "x", "1", Instant.now(), Map.of());

    @Test
    void currentPublicationMapsToV1() {
        AnnounceContent content = new AnnounceContent("Bike", "brief", "desc", List.of(), List.of("pic.jpg"),
                new CategoryId(1036000), new Location(null, "Kolding", "6000", null, null));
        GoodsDetails details = new GoodsDetails(Money.of(new BigDecimal("12.50")), Condition.GOOD, SaleType.FIXED_PRICE,
                null, "pcs", null, null, null);
        LocalDateTime at = LocalDateTime.of(2026, 9, 26, 12, 0);
        AnnouncePublished event = new AnnouncePublished(at, at.plusDays(30), new SellerId(5), content, details);

        AnnouncePublishedV1 v1 = AnnounceMessaging.toPublished(event, ENVELOPE, "DKK");

        assertThat(v1.announceId()).isEqualTo("abc");
        assertThat(v1.kind()).isEqualTo("GOODS");
        assertThat(v1.sellerId()).isEqualTo(5);
        assertThat(v1.priceCents()).isEqualTo(1250L);
        assertThat(v1.saleType()).isEqualTo("FIXED_PRICE");
        assertThat(v1.categoryId()).isEqualTo(1036000);
        assertThat(v1.mainPicture()).isEqualTo("pic.jpg");
    }

    @Test
    void legacyPublicationWithoutDetailsIsSkipped() {
        LocalDateTime at = LocalDateTime.of(2026, 9, 26, 12, 0);
        AnnouncePublished legacy = new AnnouncePublished(at, at.plusDays(30), null, null, null);

        assertThat(AnnounceMessaging.toPublished(legacy, ENVELOPE, "DKK")).isNull();
    }

    @Test
    void legacyEditWithoutDetailsIsSkipped() {
        assertThat(AnnounceMessaging.toEdited(new AnnounceEdited(null, null, LocalDateTime.now()), ENVELOPE, "DKK")).isNull();
    }
}
