package org.linlinjava.announce.auction.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.linlinjava.announce.auction.domain.events.AuctionCancelled;
import org.linlinjava.announce.auction.domain.events.AuctionClosedUnsold;
import org.linlinjava.announce.auction.domain.events.AuctionClosedWon;
import org.linlinjava.announce.auction.domain.events.AuctionExtended;
import org.linlinjava.announce.auction.domain.events.AuctionOpened;
import org.linlinjava.announce.auction.domain.events.AuctionSnagged;
import org.linlinjava.announce.auction.domain.events.BidCancelled;
import org.linlinjava.announce.auction.domain.events.BidPlaced;
import org.linlinjava.announce.auction.domain.events.BidRetracted;
import org.linlinjava.announce.auction.domain.events.BidderOutbid;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * State of the Auction aggregate under {@code /auction/{announceId}}, rebuilt from its events: one timed auction per
 * announce (ActionBazaar's Item with initialPrice, bids and a timed end; {@code doc/auction-chat-schema.md} §3).
 * Immutable; the rules that guard each command live in {@link AuctionRules}. Amounts are minor units.
 */
public record Auction(String id,
                      Integer sellerId,
                      String title,
                      String mainPicture,
                      String currency,
                      long initialPriceCents,
                      Long reservePriceCents,
                      Long snagItPriceCents,
                      long incrementCents,
                      LocalDateTime startsAt,
                      LocalDateTime endsAt,
                      int autoExtendSec,
                      AuctionStatus status,
                      List<Bid> bids,
                      Integer winnerId,
                      String winningBidId,
                      long finalPriceCents,
                      LocalDateTime closedAt,
                      LocalDateTime updatedAt) {

    public Auction {
        bids = bids == null ? List.of() : List.copyOf(bids);
    }

    /** The current highest standing bid, if any. */
    @JsonIgnore
    public Optional<Bid> activeBid() {
        return bids.stream().filter(b -> b.status() == BidStatus.ACTIVE).findFirst();
    }

    /** The price a bidder must beat: the highest standing bid, else the start price. */
    @JsonIgnore
    public long currentPriceCents() {
        return activeBid().map(Bid::amountCents).orElse(initialPriceCents);
    }

    @JsonIgnore
    public long standingBidCount() {
        return bids.stream().filter(b -> b.status().isStanding()).count();
    }

    @JsonIgnore
    public boolean hasBids() {
        return standingBidCount() > 0;
    }

    /** The next bid a buyer must place at least: start price for the first bid, current price plus increment after that. */
    @JsonIgnore
    public long minimumNextBidCents() {
        return hasBids() ? currentPriceCents() + incrementCents : initialPriceCents;
    }

    public Optional<Bid> bid(String bidId) {
        return bids.stream().filter(b -> b.id().equals(bidId)).findFirst();
    }

    // ------------------------------------------------------------------ state rebuilding

    public static Auction apply(String id, AuctionOpened e) {
        return new Auction(id, e.sellerId(), e.title(), e.mainPicture(), e.currency(), e.initialPriceCents(), e.reservePriceCents(),
                e.snagItPriceCents(), e.incrementCents(), e.startsAt(), e.endsAt(), e.autoExtendSec(), AuctionStatus.OPEN, List.of(),
                null, null, 0L, null, e.at());
    }

    public Auction apply(BidPlaced e) {
        List<Bid> next = new ArrayList<>(bids);
        next.add(new Bid(e.bidId(), e.bidderId(), e.amountCents(), e.maxAmountCents(), e.at(), BidStatus.ACTIVE));
        return withBids(next).withEnd(e.endsAt(), e.at());
    }

    public Auction apply(BidderOutbid e) {
        return withBids(mark(e.bidId(), BidStatus.OUTBID)).withEnd(endsAt, e.at());
    }

    public Auction apply(AuctionExtended e) {
        return withEnd(e.endsAt(), e.at());
    }

    public Auction apply(AuctionSnagged e) {
        return closed(AuctionStatus.CLOSED_SNAGGED, e.buyerId(), null, e.priceCents(), e.at());
    }

    public Auction apply(AuctionClosedWon e) {
        return withBids(mark(e.winningBidId(), BidStatus.WON)).closed(AuctionStatus.CLOSED_WON, e.winnerId(), e.winningBidId(), e.priceCents(), e.at());
    }

    public Auction apply(AuctionClosedUnsold e) {
        return closed(AuctionStatus.CLOSED_UNSOLD, null, null, 0L, e.at());
    }

    public Auction apply(AuctionCancelled e) {
        return closed(AuctionStatus.CANCELLED, null, null, 0L, e.at());
    }

    public Auction apply(BidRetracted e) {
        return withBids(reactivate(mark(e.bidId(), BidStatus.RETRACTED), e.newActiveBidId())).withEnd(endsAt, e.at());
    }

    public Auction apply(BidCancelled e) {
        return withBids(reactivate(mark(e.bidId(), BidStatus.CANCELLED), e.newActiveBidId())).withEnd(endsAt, e.at());
    }

    // ------------------------------------------------------------------ helpers

    /** The bid that becomes current when {@code removedBidId} is taken out: the highest standing one that is left. */
    public Optional<Bid> nextActiveWithout(String removedBidId) {
        return bids.stream().filter(b -> !b.id().equals(removedBidId) && b.status().isStanding())
                .max(Comparator.comparingLong(Bid::amountCents).thenComparing(Bid::at, Comparator.reverseOrder()));
    }

    private List<Bid> mark(String bidId, BidStatus status) {
        return bids.stream().map(b -> b.id().equals(bidId) ? b.withStatus(status) : b).toList();
    }

    private static List<Bid> reactivate(List<Bid> list, String activeBidId) {
        return list.stream().map(b -> b.status() == BidStatus.ACTIVE && !b.id().equals(activeBidId) ? b.withStatus(BidStatus.OUTBID)
                : b.id().equals(activeBidId) && b.status() == BidStatus.OUTBID ? b.withStatus(BidStatus.ACTIVE) : b).toList();
    }

    private Auction withBids(List<Bid> next) {
        return new Auction(id, sellerId, title, mainPicture, currency, initialPriceCents, reservePriceCents, snagItPriceCents, incrementCents,
                startsAt, endsAt, autoExtendSec, status, next, winnerId, winningBidId, finalPriceCents, closedAt, updatedAt);
    }

    private Auction withEnd(LocalDateTime newEndsAt, LocalDateTime at) {
        return new Auction(id, sellerId, title, mainPicture, currency, initialPriceCents, reservePriceCents, snagItPriceCents, incrementCents,
                startsAt, newEndsAt, autoExtendSec, status, bids, winnerId, winningBidId, finalPriceCents, closedAt, at);
    }

    private Auction closed(AuctionStatus newStatus, Integer winner, String winningBid, long price, LocalDateTime at) {
        return new Auction(id, sellerId, title, mainPicture, currency, initialPriceCents, reservePriceCents, snagItPriceCents, incrementCents,
                startsAt, endsAt, autoExtendSec, newStatus, bids, winner, winningBid, price, at, at);
    }
}
