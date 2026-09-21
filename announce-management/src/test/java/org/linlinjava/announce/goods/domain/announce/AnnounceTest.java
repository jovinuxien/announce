package org.linlinjava.announce.goods.domain.announce;

import org.junit.jupiter.api.Test;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceExpired;
import org.linlinjava.announce.goods.domain.announce.events.AnnouncePriceChanged;
import org.linlinjava.announce.goods.domain.announce.events.AnnouncePublished;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceWithdrawn;
import org.linlinjava.announce.goods.domain.announce.events.StockAdjusted;
import org.linlinjava.announce.goods.domain.category.CategoryId;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnounceTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 20, 12, 0);
    static final SellerId SELLER = new SellerId(7);
    static final Location STOCKHOLM = Location.inRegion(1, "Stockholm", "11122");

    static AnnounceDetails details(SaleType saleType, Money price, Location location) {
        return new AnnounceDetails("Bike", "Almost new", "<p>desc</p>", List.of("bike"), List.of("http://x/1.jpg"), "pcs",
                new CategoryId(1), null, saleType, price, location, List.of(new Attribute("Colour", "Red")), List.of());
    }

    static Announce persisted(AnnounceDetails d) {
        Announce a = Announce.draft(SELLER, d, NOW);
        a.assignId(new AnnounceId(100));
        return a;
    }

    @Test
    void draftIsNotVisibleAndHasAReference() {
        Announce a = Announce.draft(SELLER, details(SaleType.FIXED_PRICE, Money.of("10"), STOCKHOLM), NOW);
        assertEquals(AnnounceStatus.DRAFT, a.status());
        assertFalse(a.isVisible());
        assertTrue(a.reference().startsWith("A20260920-"));
        assertNull(a.id());
    }

    @Test
    void publishNeedsALocation() {
        Announce a = persisted(details(SaleType.FIXED_PRICE, Money.of("10"), null));
        AnnounceRuleViolation e = assertThrows(AnnounceRuleViolation.class, () -> a.publish(Duration.ofDays(30), NOW));
        assertTrue(e.getMessage().contains("location"));
    }

    @Test
    void fixedPriceNeedsAPriceUnlessVariantsCarryIt() {
        Announce a = persisted(details(SaleType.FIXED_PRICE, Money.zero(), STOCKHOLM));
        assertThrows(AnnounceRuleViolation.class, () -> a.publish(Duration.ofDays(30), NOW));
        a.addVariant(List.of("M"), Money.of("12"), 1, "", NOW);
        a.pullEvents();
        a.publish(Duration.ofDays(30), NOW);
        assertEquals(AnnounceStatus.PUBLISHED, a.status());
    }

    @Test
    void publishSetsExpiryAndRecordsEvent() {
        Announce a = persisted(details(SaleType.FIXED_PRICE, Money.of("10"), STOCKHOLM));
        a.publish(Duration.ofDays(60), NOW);
        assertTrue(a.isVisible());
        assertEquals(NOW.plusDays(60), a.expiresAt());
        var events = a.pullEvents();
        assertEquals(1, events.size());
        AnnouncePublished published = assertInstanceOf(AnnouncePublished.class, events.get(0));
        assertEquals(new AnnounceId(100), published.announceId());
        assertEquals(SaleType.FIXED_PRICE, published.saleType());
        assertTrue(a.pullEvents().isEmpty(), "events are cleared once pulled");
    }

    @Test
    void giveawayPublishesWithoutPriceAndCannotBeRepriced() {
        Announce a = persisted(details(SaleType.GIVEAWAY, Money.zero(), STOCKHOLM));
        a.publish(Duration.ofDays(30), NOW);
        assertThrows(AnnounceRuleViolation.class, () -> a.changePrice(Money.of("5"), NOW));
        assertThrows(IllegalArgumentException.class, () -> details(SaleType.GIVEAWAY, Money.of("5"), STOCKHOLM));
    }

    @Test
    void auctionPriceIsNotChangedHere() {
        Announce a = persisted(details(SaleType.AUCTION, Money.of("100"), STOCKHOLM));
        a.publish(Duration.ofDays(7), NOW);
        assertThrows(AnnounceRuleViolation.class, () -> a.changePrice(Money.of("120"), NOW));
    }

    @Test
    void withdrawThenRepublish() {
        Announce a = persisted(details(SaleType.FIXED_PRICE, Money.of("10"), STOCKHOLM));
        assertThrows(AnnounceRuleViolation.class, () -> a.withdraw(NOW), "a draft cannot be withdrawn");
        a.publish(Duration.ofDays(30), NOW);
        a.withdraw(NOW.plusDays(1));
        assertEquals(AnnounceStatus.WITHDRAWN, a.status());
        assertInstanceOf(AnnounceWithdrawn.class, a.pullEvents().get(1));
        a.publish(Duration.ofDays(30), NOW.plusDays(2));
        assertEquals(AnnounceStatus.PUBLISHED, a.status());
        assertEquals(NOW.plusDays(32), a.expiresAt());
    }

    @Test
    void soldIsFinal() {
        Announce a = persisted(details(SaleType.FIXED_PRICE, Money.of("10"), STOCKHOLM));
        a.publish(Duration.ofDays(30), NOW);
        a.markSold(NOW);
        assertEquals(AnnounceStatus.SOLD, a.status());
        assertThrows(AnnounceRuleViolation.class, () -> a.publish(Duration.ofDays(30), NOW));
        assertThrows(AnnounceRuleViolation.class, () -> a.edit(details(SaleType.FIXED_PRICE, Money.of("11"), STOCKHOLM), NOW));
        assertThrows(AnnounceRuleViolation.class, () -> a.addVariant(List.of(), Money.of("1"), 1, "", NOW));
    }

    @Test
    void editingThePriceRecordsAPriceChange() {
        Announce a = persisted(details(SaleType.FIXED_PRICE, Money.of("10"), STOCKHOLM));
        a.edit(details(SaleType.FIXED_PRICE, Money.of("8"), STOCKHOLM), NOW);
        var events = a.pullEvents();
        assertEquals(2, events.size());
        AnnouncePriceChanged change = assertInstanceOf(AnnouncePriceChanged.class, events.get(1));
        assertEquals(Money.of("10"), change.previous());
        assertEquals(Money.of("8"), change.current());
    }

    @Test
    void stockNeverGoesNegativeAndSoldOutIsSignalled() {
        Announce a = persisted(details(SaleType.FIXED_PRICE, Money.of("10"), STOCKHOLM));
        Variant v = a.addVariant(List.of("M"), Money.of("10"), 2, "", NOW);
        v.assignId(55);
        a.pullEvents();
        assertThrows(AnnounceRuleViolation.class, () -> a.adjustStock(55, -3, NOW));
        a.adjustStock(55, -2, NOW);
        StockAdjusted adjusted = assertInstanceOf(StockAdjusted.class, a.pullEvents().get(0));
        assertTrue(adjusted.soldOut());
        assertEquals(0, v.stock());
        assertThrows(AnnounceRuleViolation.class, () -> a.adjustStock(999, 1, NOW), "unknown variant");
    }

    @Test
    void expiresOnlyWhenDue() {
        Announce a = persisted(details(SaleType.FIXED_PRICE, Money.of("10"), STOCKHOLM));
        assertFalse(a.expireIfDue(NOW), "a draft never expires");
        a.publish(Duration.ofDays(1), NOW);
        assertFalse(a.expireIfDue(NOW.plusHours(23)));
        assertTrue(a.expireIfDue(NOW.plusDays(1)));
        assertEquals(AnnounceStatus.EXPIRED, a.status());
        assertInstanceOf(AnnounceExpired.class, a.pullEvents().get(1));
        assertFalse(a.expireIfDue(NOW.plusDays(2)), "expiring twice is a no-op");
    }

    @Test
    void locationRules() {
        assertThrows(IllegalArgumentException.class, () -> new Location(null, " ", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Location(1, "X", null, java.math.BigDecimal.ONE, null));
        assertThrows(IllegalArgumentException.class, () -> new Location(1, "X", null, java.math.BigDecimal.valueOf(91), java.math.BigDecimal.ONE));
        Location l = new Location(null, " Malmö ", "21122", java.math.BigDecimal.valueOf(55.6), java.math.BigDecimal.valueOf(13.0));
        assertEquals("Malmö", l.city());
        assertTrue(l.hasCoordinates());
    }
}
