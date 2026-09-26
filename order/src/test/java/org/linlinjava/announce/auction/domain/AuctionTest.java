package org.linlinjava.announce.auction.domain;

import org.junit.jupiter.api.Test;
import org.linlinjava.announce.auction.domain.AuctionCommands.CancelAuction;
import org.linlinjava.announce.auction.domain.AuctionCommands.CloseAuction;
import org.linlinjava.announce.auction.domain.AuctionCommands.OpenAuction;
import org.linlinjava.announce.auction.domain.AuctionCommands.PlaceBid;
import org.linlinjava.announce.auction.domain.AuctionCommands.RetractBid;
import org.linlinjava.announce.auction.domain.AuctionCommands.SnagIt;
import org.linlinjava.announce.auction.domain.events.AuctionCancelled;
import org.linlinjava.announce.auction.domain.events.AuctionClosedUnsold;
import org.linlinjava.announce.auction.domain.events.AuctionClosedWon;
import org.linlinjava.announce.auction.domain.events.AuctionExtended;
import org.linlinjava.announce.auction.domain.events.AuctionOpened;
import org.linlinjava.announce.auction.domain.events.AuctionSnagged;
import org.linlinjava.announce.auction.domain.events.BidPlaced;
import org.linlinjava.announce.auction.domain.events.BidRetracted;
import org.linlinjava.announce.auction.domain.events.BidderOutbid;
import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.cqrs.test.CommandFixture;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Auction aggregate through the framework's given/when/then fixture: the rules of doc/auction-chat-schema.md §3. */
class AuctionTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"), ZoneOffset.UTC);
    static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    static final String ID = "announce-1";
    static final int SELLER = 7;
    static final int ALICE = 9;
    static final int BOB = 10;

    static CommandFixture<Auction> fixture() {
        return CommandFixture.forAggregate(Auction.class).handlers(AuctionHandlers.definitions(CLOCK)).subject(AuctionCommands.subjectOf(ID));
    }

    static AuctionOpened opened(Long reserve, Long snagIt, int autoExtendSec, LocalDateTime endsAt) {
        return new AuctionOpened(SELLER, "Bike", "http://x/1.jpg", "DKK", 10000, reserve, snagIt, 1000, NOW.minusDays(1), endsAt, autoExtendSec, NOW.minusDays(1));
    }

    static AuctionOpened opened() {
        return opened(null, null, 0, NOW.plusDays(6));
    }

    static BidPlaced bid(String id, int bidder, long amount) {
        return new BidPlaced(id, bidder, amount, null, NOW.minusHours(1), NOW.plusDays(6));
    }

    /** A bid on an auction that ended a minute ago (the event carries the end as it stood). */
    static BidPlaced lateBid(String id, int bidder, long amount) {
        return new BidPlaced(id, bidder, amount, null, NOW.minusHours(1), NOW.minusMinutes(1));
    }

    @Test
    void openingChecksTheWindowAndPrices() {
        fixture().when(new OpenAuction(ID, SELLER, "Bike", "", "DKK", 10000, null, null, 1000, NOW, NOW.plusDays(7), 120))
                .thenExpectEvents(new AuctionOpened(SELLER, "Bike", "", "DKK", 10000, null, null, 1000, NOW, NOW.plusDays(7), 120, NOW));
        assertEquals("auction-window", rejected(fixture().when(new OpenAuction(ID, SELLER, "Bike", "", "DKK", 10000, null, null, 1000, NOW.plusDays(7), NOW, 0))).getCode());
        assertEquals("reserve-below-start", rejected(fixture().when(new OpenAuction(ID, SELLER, "Bike", "", "DKK", 10000, 5000L, null, 1000, NOW, NOW.plusDays(7), 0))).getCode());
    }

    @Test
    void theFirstBidMeetsTheStartPriceAndTheNextOnesTheIncrement() {
        Auction one = fixture().given(opened()).when(new PlaceBid(ID, "b1", ALICE, 10000, null))
                .thenExpectEvents(new BidPlaced("b1", ALICE, 10000, null, NOW, NOW.plusDays(6))).state();
        assertEquals(10000, one.currentPriceCents());
        assertEquals(11000, one.minimumNextBidCents());

        // below current + increment
        assertEquals("bid-below-minimum", rejected(fixture().given(opened(), bid("b1", ALICE, 10000)).when(new PlaceBid(ID, "b2", BOB, 10500, null))).getCode());
        // the previous bidder is outbid
        fixture().given(opened(), bid("b1", ALICE, 10000)).when(new PlaceBid(ID, "b2", BOB, 11000, null))
                .thenExpectEvents(new BidderOutbid("b1", ALICE, "b2", NOW), new BidPlaced("b2", BOB, 11000, null, NOW, NOW.plusDays(6)));
    }

    @Test
    void theSellerCannotBidAndBidsNeedAnOpenWindow() {
        assertEquals("seller-cannot-bid", rejected(fixture().given(opened()).when(new PlaceBid(ID, "b1", SELLER, 10000, null))).getCode());
        assertEquals("auction-ended", rejected(fixture().given(opened(null, null, 0, NOW.minusMinutes(1))).when(new PlaceBid(ID, "b1", ALICE, 10000, null))).getCode());
        assertEquals("auction-not-open", rejected(fixture().given(opened(), new AuctionCancelled("x", SELLER, "USER", NOW)).when(new PlaceBid(ID, "b1", ALICE, 10000, null))).getCode());
    }

    @Test
    void aBidInsideTheAutoExtendWindowPushesTheEnd() {
        LocalDateTime end = NOW.plusSeconds(30);
        fixture().given(opened(null, null, 120, end)).when(new PlaceBid(ID, "b1", ALICE, 10000, null))
                .thenExpectEvents(new BidPlaced("b1", ALICE, 10000, null, NOW, NOW.plusSeconds(120)), new AuctionExtended(end, NOW.plusSeconds(120), NOW));
    }

    @Test
    void snagItOnlyWhileNoBidExists() {
        fixture().given(opened(null, 20000L, 0, NOW.plusDays(6))).when(new SnagIt(ID, ALICE)).thenExpectEvents(new AuctionSnagged(ALICE, 20000, NOW));
        assertEquals("bids-exist", rejected(fixture().given(opened(null, 20000L, 0, NOW.plusDays(6)), bid("b1", BOB, 10000)).when(new SnagIt(ID, ALICE))).getCode());
        assertEquals("no-snag-it-price", rejected(fixture().given(opened()).when(new SnagIt(ID, ALICE))).getCode());
    }

    @Test
    void closingIsIdempotentAndTheReserveDecides() {
        fixture().given(opened()).when(new CloseAuction(ID)).thenExpectNoEvents();   // not due
        AuctionOpened due = opened(null, null, 0, NOW.minusMinutes(1));
        fixture().given(due).when(new CloseAuction(ID)).thenExpectEvents(new AuctionClosedUnsold("no bids", NOW));
        fixture().given(due, lateBid("b1", ALICE, 10000), new BidderOutbid("b1", ALICE, "b2", NOW), lateBid("b2", BOB, 12000)).when(new CloseAuction(ID))
                .thenExpectEvents(new AuctionClosedWon(BOB, "b2", 12000, NOW));
        AuctionOpened reserved = opened(15000L, null, 0, NOW.minusMinutes(1));
        fixture().given(reserved, lateBid("b1", ALICE, 12000)).when(new CloseAuction(ID)).thenExpectEvents(new AuctionClosedUnsold("reserve not met", NOW));
        fixture().given(due, new AuctionClosedUnsold("no bids", NOW)).when(new CloseAuction(ID)).thenExpectNoEvents();
    }

    @Test
    void theSellerCancelsOnlyBeforeTheFirstBidStaffAnyTime() {
        fixture().given(opened()).when(new CancelAuction(ID, SELLER, "USER", "changed my mind")).thenExpectEvents(new AuctionCancelled("changed my mind", SELLER, "USER", NOW));
        assertEquals("bids-exist", rejected(fixture().given(opened(), bid("b1", ALICE, 10000)).when(new CancelAuction(ID, SELLER, "USER", "x"))).getCode());
        CommandRejectedException stranger = rejected(fixture().given(opened()).when(new CancelAuction(ID, BOB, "USER", "x")));
        assertEquals(HttpStatus.FORBIDDEN, stranger.getStatus());
        fixture().given(opened(), bid("b1", ALICE, 10000)).when(new CancelAuction(ID, 1, "ADMIN", "fraud")).thenExpectEvents(new AuctionCancelled("fraud", 1, "ADMIN", NOW));
        fixture().given(opened(), new AuctionCancelled("x", SELLER, "USER", NOW)).when(new CancelAuction(ID, null, "SYSTEM", "gone")).thenExpectNoEvents();
    }

    @Test
    void retractingTheHighestBidReinstatesThePrevious() {
        fixture().given(opened(), bid("b1", ALICE, 10000), new BidderOutbid("b1", ALICE, "b2", NOW), bid("b2", BOB, 12000)).when(new RetractBid(ID, "b2", BOB))
                .thenExpectEvents(new BidRetracted("b2", BOB, "b1", 10000, NOW));
        Auction after = fixture().given(opened(), bid("b1", ALICE, 10000), new BidderOutbid("b1", ALICE, "b2", NOW), bid("b2", BOB, 12000),
                new BidRetracted("b2", BOB, "b1", 10000, NOW)).when(new CloseAuction(ID)).state();
        assertEquals(10000, after.currentPriceCents());
        assertEquals("b1", after.activeBid().orElseThrow().id());
    }

    private static CommandRejectedException rejected(CommandFixture<Auction>.Then then) {
        return then.thenExpectException(CommandRejectedException.class);
    }
}
