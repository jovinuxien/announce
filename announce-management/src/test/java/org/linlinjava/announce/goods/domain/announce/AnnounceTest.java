package org.linlinjava.announce.goods.domain.announce;

import org.junit.jupiter.api.Test;
import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.cqrs.test.CommandFixture;
import org.linlinjava.announce.goods.domain.announce.AnnounceCommands.EditAnnounce;
import org.linlinjava.announce.goods.domain.announce.AnnounceCommands.ExpireAnnounce;
import org.linlinjava.announce.goods.domain.announce.AnnounceCommands.MarkSold;
import org.linlinjava.announce.goods.domain.announce.AnnounceCommands.ModerateExpire;
import org.linlinjava.announce.goods.domain.announce.AnnounceCommands.ModerateWithdraw;
import org.linlinjava.announce.goods.domain.announce.AnnounceCommands.PostAnnounce;
import org.linlinjava.announce.goods.domain.announce.AnnounceCommands.PublishAnnounce;
import org.linlinjava.announce.goods.domain.announce.AnnounceCommands.RelistAnnounce;
import org.linlinjava.announce.goods.domain.announce.AnnounceCommands.WithdrawAnnounce;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceEdited;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceExpired;
import org.linlinjava.announce.goods.domain.announce.events.AnnouncePosted;
import org.linlinjava.announce.goods.domain.announce.events.AnnouncePublished;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceSold;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceWithdrawn;
import org.linlinjava.announce.goods.domain.category.CategoryId;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The Announce aggregate through the framework's given/when/then fixture: one path per kind and every kind invariant. */
class AnnounceTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC);
    static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    static final String ID = "a1";
    static final SellerId OWNER = new SellerId(7);
    static final SellerId STRANGER = new SellerId(8);
    static final Location STOCKHOLM = Location.inRegion(1, "Stockholm", "11122");

    static CommandFixture<Announce> fixture() {
        return CommandFixture.forAggregate(Announce.class).handlers(AnnounceHandlers.definitions(CLOCK)).subject(AnnounceCommands.subjectOf(ID));
    }

    static AnnounceContent content(String title, Location location) {
        return new AnnounceContent(title, "brief", "<p>desc</p>", List.of("k"), List.of("http://x/1.jpg"), new CategoryId(3), location);
    }

    static GoodsDetails bike() {
        return GoodsDetails.fixedPrice(Money.of("1500"), Condition.GOOD);
    }

    static JobDetails developer() {
        return JobDetails.of("Acme", ContractType.FULL_TIME, new SalaryRange(new BigDecimal("40000"), new BigDecimal("55000"), "SEK"),
                NOW.plusDays(30), "jobs@acme.invalid");
    }

    static EventDetails market(LocalDateTime start, LocalDateTime end) {
        return EventDetails.free(start, end, "Town square", "The city");
    }

    static AnnouncePosted posted(AnnounceKind details) {
        return new AnnouncePosted(OWNER, content("Bike", STOCKHOLM), details, NOW.minusDays(1));
    }

    static AnnouncePublished published() {
        return published(bike(), NOW.minusHours(1), NOW.minusHours(1).plus(Announce.DEFAULT_VALIDITY));
    }

    /** The published event carries the owner, content and details as they were, for the public event relay. */
    static AnnouncePublished published(AnnounceKind details, LocalDateTime at, LocalDateTime expiresAt) {
        return new AnnouncePublished(at, expiresAt, OWNER, content("Bike", STOCKHOLM), details);
    }

    // ------------------------------------------------------------------ one path per kind

    @Test
    void goodsArePostedPublishedAndSold() {
        Announce draft = fixture().when(new PostAnnounce(ID, OWNER, content("Bike", STOCKHOLM), bike()))
                .thenExpectEvents(new AnnouncePosted(OWNER, content("Bike", STOCKHOLM), bike(), NOW))
                .state();
        assertEquals(AnnounceStatus.DRAFT, draft.status());
        assertEquals(Kind.GOODS, draft.kind());
        assertNull(draft.publishedAt());

        Announce live = fixture().given(posted(bike())).when(new PublishAnnounce(ID, OWNER, null))
                .thenExpectEvents(published(bike(), NOW, NOW.plus(Announce.DEFAULT_VALIDITY)))
                .state();
        assertEquals(AnnounceStatus.PUBLISHED, live.status());
        assertEquals(NOW.plusDays(60), live.expiresAt());

        fixture().given(posted(bike()), published()).when(new MarkSold(ID, OWNER))
                .thenExpectEvents(new AnnounceSold(NOW));
        assertEquals("announce-sold", rejected(fixture().given(posted(bike()), published(), new AnnounceSold(NOW))
                .when(new EditAnnounce(ID, OWNER, content("Bike", STOCKHOLM), bike()))).getCode());
    }

    @Test
    void jobsArePostedAndPublishedButNeverSold() {
        fixture().when(new PostAnnounce(ID, OWNER, content("Java developer", STOCKHOLM), developer()))
                .thenExpectEvents(new AnnouncePosted(OWNER, content("Java developer", STOCKHOLM), developer(), NOW));
        Announce live = fixture().given(posted(developer())).when(new PublishAnnounce(ID, OWNER, Duration.ofDays(14)))
                .thenExpectEvents(published(developer(), NOW, NOW.plusDays(14))).state();
        assertEquals(Kind.JOB, live.kind());
        assertEquals("sold-not-applicable", rejected(fixture().given(posted(developer()), published(developer(), NOW.minusHours(1), NOW.plusDays(13))).when(new MarkSold(ID, OWNER))).getCode());
        fixture().given(posted(developer()), published(developer(), NOW.minusHours(1), NOW.plusDays(13))).when(new WithdrawAnnounce(ID, OWNER)).thenExpectEvents(new AnnounceWithdrawn(NOW));
    }

    @Test
    void eventsArePostedAndPublished() {
        EventDetails market = market(NOW.plusDays(3), NOW.plusDays(3).plusHours(6));
        fixture().when(new PostAnnounce(ID, OWNER, content("Flea market", STOCKHOLM), market))
                .thenExpectEvents(new AnnouncePosted(OWNER, content("Flea market", STOCKHOLM), market, NOW));
        Announce live = fixture().given(posted(market)).when(new PublishAnnounce(ID, OWNER, null))
                .thenExpectEvents(published(market, NOW, NOW.plus(Announce.DEFAULT_VALIDITY))).state();
        assertEquals(Kind.EVENT, live.kind());
        assertEquals("sold-not-applicable", rejected(fixture().given(posted(market), published(market, NOW.minusHours(1), NOW.plusDays(59))).when(new MarkSold(ID, OWNER))).getCode());
    }

    // ------------------------------------------------------------------ kind invariants

    @Test
    void anEventMustStartBeforeItEndsAndEndInTheFuture() {
        EventDetails backwards = market(NOW.plusDays(3), NOW.plusDays(2));
        assertEquals("event-dates", rejected(fixture().given(posted(backwards)).when(new PublishAnnounce(ID, OWNER, null))).getCode());
        EventDetails over = market(NOW.minusDays(2), NOW.minusDays(1));
        assertEquals("event-in-the-past", rejected(fixture().given(posted(over)).when(new PublishAnnounce(ID, OWNER, null))).getCode());
    }

    @Test
    void jobsAndEventsCannotBeAuctioned() {
        JobDetails auctionedJob = new JobDetails("Acme", ContractType.FULL_TIME, null, null, "jobs@acme.invalid", SaleType.AUCTION);
        CommandRejectedException e = rejected(fixture().when(new PostAnnounce(ID, OWNER, content("Job", STOCKHOLM), auctionedJob)));
        assertEquals("not-biddable", e.getCode());
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        EventDetails auctionedEvent = new EventDetails(NOW.plusDays(1), NOW.plusDays(2), "", "", false, null, null, SaleType.AUCTION);
        assertEquals("not-biddable", rejected(fixture().given(posted(market(NOW.plusDays(1), NOW.plusDays(2))))
                .when(new EditAnnounce(ID, OWNER, content("Event", STOCKHOLM), auctionedEvent))).getCode());
    }

    @Test
    void goodsNeedAPriceUnlessGiveawayOrAuction() {
        GoodsDetails unpriced = new GoodsDetails(Money.zero(), null, SaleType.FIXED_PRICE, null, null, null, null, null, null, null);
        assertEquals("price-required", rejected(fixture().given(posted(unpriced)).when(new PublishAnnounce(ID, OWNER, null))).getCode());
        GoodsDetails giveaway = new GoodsDetails(Money.zero(), null, SaleType.GIVEAWAY, null, null, null, null, null, null, null);
        fixture().given(posted(giveaway)).when(new PublishAnnounce(ID, OWNER, null)).thenExpectNoError();
        GoodsDetails auction = new GoodsDetails(Money.zero(), null, SaleType.AUCTION, null, null, null, null, null, null, null);
        fixture().given(posted(auction)).when(new PublishAnnounce(ID, OWNER, null)).thenExpectNoError();
        GoodsDetails withVariant = new GoodsDetails(Money.zero(), null, SaleType.FIXED_PRICE, null, null,
                List.of(new Variant(List.of("M"), Money.of("10"), 1, null)), null, null, null, null);
        fixture().given(posted(withVariant)).when(new PublishAnnounce(ID, OWNER, null)).thenExpectNoError();
        assertThrows(IllegalArgumentException.class, () -> new GoodsDetails(Money.of("5"), null, SaleType.GIVEAWAY, null, null, null, null, null, null, null));
    }

    // ------------------------------------------------------------------ auction schedule

    static GoodsDetails auction(LocalDateTime startsAt, LocalDateTime endsAt) {
        return new GoodsDetails(Money.of("100"), Condition.GOOD, SaleType.AUCTION, null, null, null, null, null, startsAt, endsAt);
    }

    @Test
    void anAuctionPublishedWithAScheduleKeepsItAndTheStateCarriesIt() {
        GoodsDetails scheduled = auction(NOW.plusDays(1), NOW.plusDays(5));
        Announce live = fixture().given(posted(scheduled)).when(new PublishAnnounce(ID, OWNER, null))
                .thenExpectEvents(published(scheduled, NOW, NOW.plus(Announce.DEFAULT_VALIDITY))).state();
        GoodsDetails goods = (GoodsDetails) live.details();
        assertEquals(NOW.plusDays(1), goods.auctionStartsAt());
        assertEquals(NOW.plusDays(5), goods.auctionEndsAt());
    }

    @Test
    void anAuctionPublishedWithoutAScheduleGetsPublishTimePlusTheDefaultDurationCappedByTheExpiry() {
        // no dates: start = publish time, end = start + 7 d
        fixture().given(posted(auction(null, null))).when(new PublishAnnounce(ID, OWNER, null))
                .thenExpectEvents(published(auction(NOW, NOW.plus(AnnounceHandlers.DEFAULT_AUCTION_DURATION)), NOW, NOW.plus(Announce.DEFAULT_VALIDITY)));
        // only a start: end = start + 7 d
        fixture().given(posted(auction(NOW.plusDays(2), null))).when(new PublishAnnounce(ID, OWNER, null))
                .thenExpectEvents(published(auction(NOW.plusDays(2), NOW.plusDays(9)), NOW, NOW.plus(Announce.DEFAULT_VALIDITY)));
        // a short validity caps the end
        fixture().given(posted(auction(null, null))).when(new PublishAnnounce(ID, OWNER, Duration.ofDays(3)))
                .thenExpectEvents(published(auction(NOW, NOW.plusDays(3)), NOW, NOW.plusDays(3)));
        // a configured duration is honoured
        CommandFixture.forAggregate(Announce.class).handlers(AnnounceHandlers.definitions(CLOCK, Duration.ofDays(2))).subject(AnnounceCommands.subjectOf(ID))
                .given(posted(auction(null, null))).when(new PublishAnnounce(ID, OWNER, null))
                .thenExpectEvents(published(auction(NOW, NOW.plusDays(2)), NOW, NOW.plus(Announce.DEFAULT_VALIDITY)));
    }

    @Test
    void anAuctionScheduleIsRefusedWhenBackwardsOnANonAuctionOrAlreadyOver() {
        // end before start, at post, at edit and at publish
        CommandRejectedException e = rejected(fixture().when(new PostAnnounce(ID, OWNER, content("Bike", STOCKHOLM), auction(NOW.plusDays(5), NOW.plusDays(1)))));
        assertEquals("auction-schedule-order", e.getCode());
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("auction-schedule-order", rejected(fixture().given(posted(auction(null, null)))
                .when(new EditAnnounce(ID, OWNER, content("Bike", STOCKHOLM), auction(NOW.plusDays(5), NOW.plusDays(5))))).getCode());
        // a schedule on a fixed-price or giveaway goods
        GoodsDetails fixedWithDates = new GoodsDetails(Money.of("100"), Condition.GOOD, SaleType.FIXED_PRICE, null, null, null, null, null, NOW.plusDays(1), NOW.plusDays(2));
        assertEquals("auction-schedule-not-allowed", rejected(fixture().when(new PostAnnounce(ID, OWNER, content("Bike", STOCKHOLM), fixedWithDates))).getCode());
        GoodsDetails giveawayWithEnd = new GoodsDetails(Money.zero(), null, SaleType.GIVEAWAY, null, null, null, null, null, null, NOW.plusDays(2));
        assertEquals("auction-schedule-not-allowed", rejected(fixture().given(posted(bike()))
                .when(new EditAnnounce(ID, OWNER, content("Bike", STOCKHOLM), giveawayWithEnd))).getCode());
        // an end in the past at publish, and an end after the expiry
        assertEquals("auction-already-over", rejected(fixture().given(posted(auction(NOW.minusDays(3), NOW.minusDays(1))))
                .when(new PublishAnnounce(ID, OWNER, null))).getCode());
        assertEquals("auction-ends-after-expiry", rejected(fixture().given(posted(auction(null, NOW.plusDays(10))))
                .when(new PublishAnnounce(ID, OWNER, Duration.ofDays(5)))).getCode());
    }

    @Test
    void editingAPublishedAuctionNeverUnsetsItsScheduleAndRevalidatesIt() {
        GoodsDetails live = auction(NOW.minusHours(1), NOW.plusDays(6));
        AnnouncePublished publishedLive = published(live, NOW.minusHours(1), NOW.minusHours(1).plus(Announce.DEFAULT_VALIDITY));
        // dates left null by the edit are carried over from the published schedule
        fixture().given(posted(auction(null, null)), publishedLive)
                .when(new EditAnnounce(ID, OWNER, content("Bike!", STOCKHOLM), auction(null, null)))
                .thenExpectEvents(new AnnounceEdited(content("Bike!", STOCKHOLM), live, NOW));
        // a new end still ahead is accepted
        fixture().given(posted(auction(null, null)), publishedLive)
                .when(new EditAnnounce(ID, OWNER, content("Bike", STOCKHOLM), auction(null, NOW.plusDays(2))))
                .thenExpectEvents(new AnnounceEdited(content("Bike", STOCKHOLM), auction(NOW.minusHours(1), NOW.plusDays(2)), NOW));
        // an end in the past or after the expiry is refused
        assertEquals("auction-already-over", rejected(fixture().given(posted(auction(null, null)), publishedLive)
                .when(new EditAnnounce(ID, OWNER, content("Bike", STOCKHOLM), auction(null, NOW.minusMinutes(1))))).getCode());
        assertEquals("auction-ends-after-expiry", rejected(fixture().given(posted(auction(null, null)), publishedLive)
                .when(new EditAnnounce(ID, OWNER, content("Bike", STOCKHOLM), auction(null, NOW.plusDays(90))))).getCode());
        // on a draft the dates are stored as given (resolved at publish)
        fixture().given(posted(auction(null, null)))
                .when(new EditAnnounce(ID, OWNER, content("Bike", STOCKHOLM), auction(NOW.plusDays(1), null)))
                .thenExpectEvents(new AnnounceEdited(content("Bike", STOCKHOLM), auction(NOW.plusDays(1), null), NOW));
    }

    @Test
    void relistingASoldAuctionReusesAScheduleStillAheadAndReplacesOneThatIsOver() {
        GoodsDetails ahead = auction(NOW.minusDays(1), NOW.plusDays(3));
        fixture().given(posted(auction(null, null)), published(ahead, NOW.minusDays(1), NOW.plusDays(59)), new AnnounceSold(NOW.minusHours(2)))
                .when(new RelistAnnounce(ID))
                .thenExpectEvents(published(ahead, NOW, NOW.plusDays(59)));
        GoodsDetails over = auction(NOW.minusDays(8), NOW.minusDays(1));
        fixture().given(posted(auction(null, null)), published(over, NOW.minusDays(8), NOW.plusDays(52)), new AnnounceSold(NOW.minusHours(2)))
                .when(new RelistAnnounce(ID))
                .thenExpectEvents(published(auction(NOW, NOW.plusDays(7)), NOW, NOW.plusDays(52)));
    }

    @Test
    void theKindNeverChangesAndOnlyTheOwnerWrites() {
        assertEquals("kind-immutable", rejected(fixture().given(posted(bike()))
                .when(new EditAnnounce(ID, OWNER, content("Bike", STOCKHOLM), developer()))).getCode());
        fixture().given(posted(bike())).when(new EditAnnounce(ID, OWNER, content("Bike, cheaper", STOCKHOLM), GoodsDetails.fixedPrice(Money.of("1400"), Condition.GOOD)))
                .thenExpectEvents(new AnnounceEdited(content("Bike, cheaper", STOCKHOLM), GoodsDetails.fixedPrice(Money.of("1400"), Condition.GOOD), NOW));
        CommandRejectedException e = rejected(fixture().given(posted(bike())).when(new PublishAnnounce(ID, STRANGER, null)));
        assertEquals("not-owner", e.getCode());
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
    }

    @Test
    void publishNeedsALocationAndAPositiveValidity() {
        AnnouncePosted noLocation = new AnnouncePosted(OWNER, content("Bike", null), bike(), NOW.minusDays(1));
        assertEquals("location-required", rejected(fixture().given(noLocation).when(new PublishAnnounce(ID, OWNER, null))).getCode());
        assertEquals("invalid-validity", rejected(fixture().given(posted(bike())).when(new PublishAnnounce(ID, OWNER, Duration.ZERO))).getCode());
    }

    @Test
    void withdrawnAndExpiredAnnouncesCanBeRepublishedAndExpiryIsIdempotent() {
        fixture().given(posted(bike()), published(), new AnnounceWithdrawn(NOW)).when(new PublishAnnounce(ID, OWNER, null))
                .thenExpectEvents(published(bike(), NOW, NOW.plus(Announce.DEFAULT_VALIDITY)));
        assertEquals("not-published", rejected(fixture().given(posted(bike())).when(new WithdrawAnnounce(ID, OWNER))).getCode());

        fixture().given(posted(bike()), published()).when(new ExpireAnnounce(ID)).thenExpectNoEvents();
        AnnouncePublished longAgo = published(bike(), NOW.minusDays(61), NOW.minusDays(1));
        fixture().given(posted(bike()), longAgo).when(new ExpireAnnounce(ID)).thenExpectEvents(new AnnounceExpired(NOW));
        fixture().given(posted(bike()), longAgo, new AnnounceExpired(NOW)).when(new ExpireAnnounce(ID)).thenExpectNoEvents();
        fixture().given(posted(bike()), longAgo, new AnnounceExpired(NOW)).when(new PublishAnnounce(ID, OWNER, null)).thenExpectNoError();
    }

    @Test
    void aSoldAnnounceIsRelistedWhenTheDealFallsThroughAndRelistIsIdempotent() {
        // sold with time left: relisted with the same expiry
        Announce relisted = fixture().given(posted(bike()), published(), new AnnounceSold(NOW.minusMinutes(5))).when(new RelistAnnounce(ID))
                .thenExpectEvents(published(bike(), NOW, published().expiresAt())).state();
        assertEquals(AnnounceStatus.PUBLISHED, relisted.status());
        // sold and expiry passed meanwhile: relisted with a fresh validity
        AnnouncePublished longAgo = published(bike(), NOW.minusDays(61), NOW.minusDays(1));
        fixture().given(posted(bike()), longAgo, new AnnounceSold(NOW.minusDays(2))).when(new RelistAnnounce(ID))
                .thenExpectEvents(published(bike(), NOW, NOW.plus(Announce.DEFAULT_VALIDITY)));
        // any other status: nothing happens
        fixture().given(posted(bike()), published()).when(new RelistAnnounce(ID)).thenExpectNoEvents();
        fixture().given(posted(bike()), published(), new AnnounceWithdrawn(NOW)).when(new RelistAnnounce(ID)).thenExpectNoEvents();
        fixture().given(posted(bike())).when(new RelistAnnounce(ID)).thenExpectNoEvents();
    }

    @Test
        void staffModeratesWithoutOwningAndTheReasonTravelsOnTheEvent() {
        fixture().given(posted(bike()), published()).when(new ModerateWithdraw(ID, 42, " spam "))
                .thenExpectEvents(new AnnounceWithdrawn(NOW, 42, "spam"));
        fixture().given(posted(bike()), published()).when(new ModerateExpire(ID, 42, "duplicate"))
                .thenExpectEvents(new AnnounceExpired(NOW, 42, "duplicate"));
        // a moderated announce is withdrawn / expired like any other and can be republished by its owner
        fixture().given(posted(bike()), published(), new AnnounceWithdrawn(NOW, 42, "spam")).when(new PublishAnnounce(ID, OWNER, null)).thenExpectNoError();
        // only published announces; reason and moderator mandatory
        assertEquals("not-published", rejected(fixture().given(posted(bike())).when(new ModerateWithdraw(ID, 42, "spam"))).getCode());
        assertEquals("not-published", rejected(fixture().given(posted(bike()), published(), new AnnounceWithdrawn(NOW)).when(new ModerateExpire(ID, 42, "x"))).getCode());
        fixture().given(posted(bike()), published()).when(new ModerateWithdraw(ID, 42, "  ")).thenExpectException(IllegalArgumentException.class);
        fixture().given(posted(bike()), published()).when(new ModerateExpire(ID, null, "x")).thenExpectException(IllegalArgumentException.class);
    }

    private static CommandRejectedException rejected(CommandFixture<Announce>.Then then) {
        return then.thenExpectException(CommandRejectedException.class);
    }
}
