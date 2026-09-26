package org.linlinjava.announce.auction.domain;

import com.opencqrs.framework.command.CommandHandler;
import com.opencqrs.framework.command.CommandHandlerDefinition;
import com.opencqrs.framework.command.StateRebuildingHandler;
import com.opencqrs.framework.command.StateRebuildingHandlerDefinition;
import org.linlinjava.announce.auction.domain.AuctionCommands.CancelAuction;
import org.linlinjava.announce.auction.domain.AuctionCommands.CancelBid;
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
import org.linlinjava.announce.auction.domain.events.BidCancelled;
import org.linlinjava.announce.auction.domain.events.BidPlaced;
import org.linlinjava.announce.auction.domain.events.BidRetracted;
import org.linlinjava.announce.auction.domain.events.BidderOutbid;
import org.linlinjava.announce.cqrs.command.Subjects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Command handling and state rebuilding of the Auction aggregate as explicit OpenCQRS definitions. Every command
 * handler checks {@link AuctionRules}, publishes its events and returns the resulting state. Because every bid and
 * snag-it on one auction is a command on the same subject, they are serialised by the event store.
 */
@Configuration(proxyBeanMethods = false)
public class AuctionHandlers {

    public static final StateRebuildingHandlerDefinition<Auction, AuctionOpened> ON_OPENED = new StateRebuildingHandlerDefinition<>(
            Auction.class, AuctionOpened.class,
            (StateRebuildingHandler.FromObjectAndMetaDataAndSubject<Auction, AuctionOpened>) (s, e, m, subject) -> Auction.apply(Subjects.idOf(subject), e));
    public static final StateRebuildingHandlerDefinition<Auction, BidPlaced> ON_BID = def(BidPlaced.class, Auction::apply);
    public static final StateRebuildingHandlerDefinition<Auction, BidderOutbid> ON_OUTBID = def(BidderOutbid.class, Auction::apply);
    public static final StateRebuildingHandlerDefinition<Auction, AuctionExtended> ON_EXTENDED = def(AuctionExtended.class, Auction::apply);
    public static final StateRebuildingHandlerDefinition<Auction, AuctionSnagged> ON_SNAGGED = def(AuctionSnagged.class, Auction::apply);
    public static final StateRebuildingHandlerDefinition<Auction, AuctionClosedWon> ON_WON = def(AuctionClosedWon.class, Auction::apply);
    public static final StateRebuildingHandlerDefinition<Auction, AuctionClosedUnsold> ON_UNSOLD = def(AuctionClosedUnsold.class, Auction::apply);
    public static final StateRebuildingHandlerDefinition<Auction, AuctionCancelled> ON_CANCELLED = def(AuctionCancelled.class, Auction::apply);
    public static final StateRebuildingHandlerDefinition<Auction, BidRetracted> ON_RETRACTED = def(BidRetracted.class, Auction::apply);
    public static final StateRebuildingHandlerDefinition<Auction, BidCancelled> ON_BID_CANCELLED = def(BidCancelled.class, Auction::apply);

    private static <E> StateRebuildingHandlerDefinition<Auction, E> def(Class<E> type, StateRebuildingHandler.FromObject<Auction, E> fn) {
        return new StateRebuildingHandlerDefinition<>(Auction.class, type, fn);
    }

    // ------------------------------------------------------------------ command handling

    public static CommandHandlerDefinition<Auction, OpenAuction, Auction> open(Clock clock) {
        return new CommandHandlerDefinition<>(Auction.class, OpenAuction.class,
                (CommandHandler.ForCommand<Auction, OpenAuction, Auction>) (c, publisher) -> {
                    AuctionRules.checkOpen(c);
                    AuctionOpened e = new AuctionOpened(c.sellerId(), c.title(), c.mainPicture(), c.currency(), c.initialPriceCents(),
                            c.reservePriceCents(), c.snagItPriceCents(), c.incrementCents(), c.startsAt(), c.endsAt(), c.autoExtendSec(), now(clock));
                    publisher.publish(e);
                    return Auction.apply(c.announceId(), e);
                });
    }

    /** Accepts the bid, outbids the previous one and extends the end when the bid lands inside the auto-extend window. */
    public static CommandHandlerDefinition<Auction, PlaceBid, Auction> placeBid(Clock clock) {
        return new CommandHandlerDefinition<>(Auction.class, PlaceBid.class,
                (CommandHandler.ForInstanceAndCommand<Auction, PlaceBid, Auction>) (state, c, publisher) -> {
                    LocalDateTime now = now(clock);
                    AuctionRules.checkBid(state, c, now);
                    Optional<Bid> previous = state.activeBid();
                    LocalDateTime endsAt = state.endsAt();
                    boolean extend = state.autoExtendSec() > 0 && now.plusSeconds(state.autoExtendSec()).isAfter(endsAt);
                    if (extend) {
                        endsAt = now.plusSeconds(state.autoExtendSec());
                    }
                    Auction next = state;
                    if (previous.isPresent()) {
                        BidderOutbid outbid = new BidderOutbid(previous.get().id(), previous.get().bidderId(), c.bidId(), now);
                        publisher.publish(outbid);
                        next = next.apply(outbid);
                    }
                    BidPlaced placed = new BidPlaced(c.bidId(), c.bidderId(), c.amountCents(), c.maxAmountCents(), now, endsAt);
                    publisher.publish(placed);
                    next = next.apply(placed);
                    if (extend) {
                        AuctionExtended extended = new AuctionExtended(state.endsAt(), endsAt, now);
                        publisher.publish(extended);
                        next = next.apply(extended);
                    }
                    return next;
                });
    }

    public static CommandHandlerDefinition<Auction, SnagIt, Auction> snagIt(Clock clock) {
        return new CommandHandlerDefinition<>(Auction.class, SnagIt.class,
                (CommandHandler.ForInstanceAndCommand<Auction, SnagIt, Auction>) (state, c, publisher) -> {
                    LocalDateTime now = now(clock);
                    AuctionRules.checkSnag(state, c, now);
                    AuctionSnagged e = new AuctionSnagged(c.buyerId(), state.snagItPriceCents(), now);
                    publisher.publish(e);
                    return state.apply(e);
                });
    }

    /** Idempotent: an auction that is not due stays as it is and no event is written. */
    public static CommandHandlerDefinition<Auction, CloseAuction, Auction> close(Clock clock) {
        return new CommandHandlerDefinition<>(Auction.class, CloseAuction.class,
                (CommandHandler.ForInstanceAndCommand<Auction, CloseAuction, Auction>) (state, c, publisher) -> {
                    LocalDateTime now = now(clock);
                    if (!AuctionRules.isDueToClose(state, now)) {
                        return state;
                    }
                    Optional<Bid> highest = state.activeBid();
                    if (highest.isPresent() && AuctionRules.meetsReserve(state, highest.get())) {
                        AuctionClosedWon e = new AuctionClosedWon(highest.get().bidderId(), highest.get().id(), highest.get().amountCents(), now);
                        publisher.publish(e);
                        return state.apply(e);
                    }
                    AuctionClosedUnsold e = new AuctionClosedUnsold(highest.isEmpty() ? "no bids" : "reserve not met", now);
                    publisher.publish(e);
                    return state.apply(e);
                });
    }

    /** SYSTEM cancels are idempotent (a closed auction is left alone); USER and ADMIN cancels are checked. */
    public static CommandHandlerDefinition<Auction, CancelAuction, Auction> cancel(Clock clock) {
        return new CommandHandlerDefinition<>(Auction.class, CancelAuction.class,
                (CommandHandler.ForInstanceAndCommand<Auction, CancelAuction, Auction>) (state, c, publisher) -> {
                    if ("SYSTEM".equals(c.actorType()) && !state.status().isOpen()) {
                        return state;
                    }
                    AuctionRules.checkCancel(state, c);
                    AuctionCancelled e = new AuctionCancelled(c.reason(), c.actorId(), c.actorType(), now(clock));
                    publisher.publish(e);
                    return state.apply(e);
                });
    }

    public static CommandHandlerDefinition<Auction, RetractBid, Auction> retractBid(Clock clock) {
        return new CommandHandlerDefinition<>(Auction.class, RetractBid.class,
                (CommandHandler.ForInstanceAndCommand<Auction, RetractBid, Auction>) (state, c, publisher) -> {
                    Bid bid = AuctionRules.checkRetract(state, c);
                    Optional<Bid> next = state.nextActiveWithout(bid.id());
                    BidRetracted e = new BidRetracted(bid.id(), bid.bidderId(), next.map(Bid::id).orElse(null),
                            next.map(Bid::amountCents).orElse(state.initialPriceCents()), now(clock));
                    publisher.publish(e);
                    return state.apply(e);
                });
    }

    public static CommandHandlerDefinition<Auction, CancelBid, Auction> cancelBid(Clock clock) {
        return new CommandHandlerDefinition<>(Auction.class, CancelBid.class,
                (CommandHandler.ForInstanceAndCommand<Auction, CancelBid, Auction>) (state, c, publisher) -> {
                    Bid bid = AuctionRules.checkCancelBid(state, c);
                    Optional<Bid> next = state.nextActiveWithout(bid.id());
                    BidCancelled e = new BidCancelled(bid.id(), c.adminId(), c.reason(), next.map(Bid::id).orElse(null),
                            next.map(Bid::amountCents).orElse(state.initialPriceCents()), now(clock));
                    publisher.publish(e);
                    return state.apply(e);
                });
    }

    /** All definitions, for {@code CommandFixture.handlers(...)}. */
    public static List<Object> definitions(Clock clock) {
        return List.of(ON_OPENED, ON_BID, ON_OUTBID, ON_EXTENDED, ON_SNAGGED, ON_WON, ON_UNSOLD, ON_CANCELLED, ON_RETRACTED, ON_BID_CANCELLED,
                open(clock), placeBid(clock), snagIt(clock), close(clock), cancel(clock), retractBid(clock), cancelBid(clock));
    }

    private static LocalDateTime now(Clock clock) {
        return LocalDateTime.now(clock);
    }

    // ------------------------------------------------------------------ beans

    @Bean StateRebuildingHandlerDefinition<Auction, AuctionOpened> auctionOnOpened() { return ON_OPENED; }
    @Bean StateRebuildingHandlerDefinition<Auction, BidPlaced> auctionOnBid() { return ON_BID; }
    @Bean StateRebuildingHandlerDefinition<Auction, BidderOutbid> auctionOnOutbid() { return ON_OUTBID; }
    @Bean StateRebuildingHandlerDefinition<Auction, AuctionExtended> auctionOnExtended() { return ON_EXTENDED; }
    @Bean StateRebuildingHandlerDefinition<Auction, AuctionSnagged> auctionOnSnagged() { return ON_SNAGGED; }
    @Bean StateRebuildingHandlerDefinition<Auction, AuctionClosedWon> auctionOnWon() { return ON_WON; }
    @Bean StateRebuildingHandlerDefinition<Auction, AuctionClosedUnsold> auctionOnUnsold() { return ON_UNSOLD; }
    @Bean StateRebuildingHandlerDefinition<Auction, AuctionCancelled> auctionOnCancelled() { return ON_CANCELLED; }
    @Bean StateRebuildingHandlerDefinition<Auction, BidRetracted> auctionOnRetracted() { return ON_RETRACTED; }
    @Bean StateRebuildingHandlerDefinition<Auction, BidCancelled> auctionOnBidCancelled() { return ON_BID_CANCELLED; }
    @Bean CommandHandlerDefinition<Auction, OpenAuction, Auction> auctionOpen(Clock clock) { return open(clock); }
    @Bean CommandHandlerDefinition<Auction, PlaceBid, Auction> auctionPlaceBid(Clock clock) { return placeBid(clock); }
    @Bean CommandHandlerDefinition<Auction, SnagIt, Auction> auctionSnagIt(Clock clock) { return snagIt(clock); }
    @Bean CommandHandlerDefinition<Auction, CloseAuction, Auction> auctionClose(Clock clock) { return close(clock); }
    @Bean CommandHandlerDefinition<Auction, CancelAuction, Auction> auctionCancel(Clock clock) { return cancel(clock); }
    @Bean CommandHandlerDefinition<Auction, RetractBid, Auction> auctionRetractBid(Clock clock) { return retractBid(clock); }
    @Bean CommandHandlerDefinition<Auction, CancelBid, Auction> auctionCancelBid(Clock clock) { return cancelBid(clock); }
}
