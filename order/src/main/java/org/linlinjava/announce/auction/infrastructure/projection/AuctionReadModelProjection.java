package org.linlinjava.announce.auction.infrastructure.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.announce.auction.domain.events.AuctionCancelled;
import org.linlinjava.announce.auction.domain.events.AuctionClosedUnsold;
import org.linlinjava.announce.auction.domain.events.AuctionClosedWon;
import org.linlinjava.announce.auction.domain.events.AuctionExtended;
import org.linlinjava.announce.auction.domain.events.AuctionOpened;
import org.linlinjava.announce.auction.domain.events.AuctionRescheduled;
import org.linlinjava.announce.auction.domain.events.AuctionSnagged;
import org.linlinjava.announce.auction.domain.events.BidCancelled;
import org.linlinjava.announce.auction.domain.events.BidPlaced;
import org.linlinjava.announce.auction.domain.events.BidRetracted;
import org.linlinjava.announce.auction.domain.events.BidderOutbid;
import org.linlinjava.announce.cqrs.command.Subjects;
import org.linlinjava.announce.cqrs.readside.EventEnvelope;
import org.linlinjava.announce.cqrs.readside.ProjectionHandlers;
import org.linlinjava.announce.cqrs.readside.ReactiveProjection;
import org.linlinjava.announce.db.dao.reactive.AnnounceAuctionEventMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceAuctionMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceBidMapper;
import org.linlinjava.announce.db.domain.AnnounceAuction;
import org.linlinjava.announce.db.domain.AnnounceAuctionEvent;
import org.linlinjava.announce.db.domain.AnnounceAuctionEventExample;
import org.linlinjava.announce.db.domain.AnnounceBid;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Group {@code auction-read-model}: keeps {@code announce_auction}, {@code announce_bid} and the append-only
 * {@code announce_auction_event} log in step with the aggregate. At-least-once: rows are upserted by id, the log is
 * keyed by the source event id, and {@code last_event_id} records the event applied.
 */
@Component
public class AuctionReadModelProjection implements ReactiveProjection {

    public static final String GROUP = "auction-read-model";

    private final AnnounceAuctionMapper auctions;
    private final AnnounceBidMapper bids;
    private final AnnounceAuctionEventMapper log;
    private final DatabaseClient db;
    private final ObjectMapper json;

    public AuctionReadModelProjection(AnnounceAuctionMapper auctions, AnnounceBidMapper bids, AnnounceAuctionEventMapper log,
                                      DatabaseClient db, ObjectMapper json) {
        this.auctions = auctions;
        this.bids = bids;
        this.log = log;
        this.db = db;
        this.json = json;
    }

    @Override
    public String group() {
        return GROUP;
    }

    @Override
    public void configure(ProjectionHandlers handlers) {
        handlers.on(AuctionOpened.class, (e, env) -> {
            AnnounceAuction row = new AnnounceAuction();
            row.setId(idOf(env));
            row.setAnnounceId(idOf(env));
            row.setSellerId(e.sellerId());
            row.setTitle(e.title());
            row.setMainPicture(e.mainPicture());
            row.setCurrency(e.currency());
            row.setInitialPrice(money(e.initialPriceCents()));
            row.setReservePrice(e.reservePriceCents() == null ? null : money(e.reservePriceCents()));
            row.setSnagItPrice(e.snagItPriceCents() == null ? null : money(e.snagItPriceCents()));
            row.setBidIncrement(money(e.incrementCents()));
            row.setCurrentPrice(money(e.initialPriceCents()));
            row.setBidCount(0);
            row.setStartTime(e.startsAt());
            row.setEndTime(e.endsAt());
            row.setAutoExtendSec(e.autoExtendSec());
            row.setStatus("OPEN");
            row.setAddTime(e.at());
            row.setDeleted(false);
            return upsertAuction(row, env, e.at()).then(logEvent(env, "OPENED", null, e.sellerId(), "USER", e, e.at()));
        });
        handlers.on(BidPlaced.class, (e, env) -> {
            AnnounceBid bid = new AnnounceBid();
            bid.setId(e.bidId());
            bid.setAuctionId(idOf(env));
            bid.setAnnounceId(idOf(env));
            bid.setBidderId(e.bidderId());
            bid.setAmount(money(e.amountCents()));
            bid.setMaxAmount(e.maxAmountCents() == null ? null : money(e.maxAmountCents()));
            bid.setBidTime(e.at());
            bid.setStatus("ACTIVE");
            bid.setAddTime(e.at());
            bid.setUpdateTime(e.at());
            bid.setDeleted(false);
            AnnounceAuction patch = new AnnounceAuction();
            patch.setId(idOf(env));
            patch.setCurrentPrice(money(e.amountCents()));
            patch.setEndTime(e.endsAt());
            return upsertBid(bid)
                    .then(db.sql("update announce_auction set bid_count = (select count(*) from announce_bid where auction_id = :id and status in ('ACTIVE','OUTBID') and deleted = 0) where id = :id")
                            .bind("id", idOf(env)).fetch().rowsUpdated())
                    .then(patchAuction(patch, env, e.at()))
                    .then(logEvent(env, "BID_PLACED", e.bidId(), e.bidderId(), "USER", e, e.at()));
        });
        handlers.on(BidderOutbid.class, (e, env) -> bidStatus(e.bidId(), "OUTBID", null, null, e.at())
                .then(logEvent(env, "OUTBID", e.bidId(), e.bidderId(), "SYSTEM", e, e.at())));
        handlers.on(AuctionExtended.class, (e, env) -> {
            AnnounceAuction patch = new AnnounceAuction();
            patch.setId(idOf(env));
            patch.setEndTime(e.endsAt());
            return patchAuction(patch, env, e.at()).then(logEvent(env, "EXTENDED", null, null, "SYSTEM", e, e.at()));
        });
        handlers.on(AuctionRescheduled.class, (e, env) -> {
            AnnounceAuction patch = new AnnounceAuction();
            patch.setId(idOf(env));
            patch.setStartTime(e.startsAt());
            patch.setEndTime(e.endsAt());
            return patchAuction(patch, env, e.at()).then(logEvent(env, "RESCHEDULED", null, null, "SYSTEM", e, e.at()));
        });
        handlers.on(AuctionSnagged.class, (e, env) -> {
            AnnounceAuction patch = new AnnounceAuction();
            patch.setId(idOf(env));
            patch.setStatus("CLOSED_SNAGGED");
            patch.setWinnerId(e.buyerId());
            patch.setCurrentPrice(money(e.priceCents()));
            patch.setClosedTime(e.at());
            return patchAuction(patch, env, e.at()).then(logEvent(env, "SNAGGED", null, e.buyerId(), "USER", e, e.at()));
        });
        handlers.on(AuctionClosedWon.class, (e, env) -> {
            AnnounceAuction patch = new AnnounceAuction();
            patch.setId(idOf(env));
            patch.setStatus("CLOSED_WON");
            patch.setWinnerId(e.winnerId());
            patch.setWinningBidId(e.winningBidId());
            patch.setCurrentPrice(money(e.priceCents()));
            patch.setClosedTime(e.at());
            return bidStatus(e.winningBidId(), "WON", null, null, e.at()).then(patchAuction(patch, env, e.at()))
                    .then(logEvent(env, "CLOSED_WON", e.winningBidId(), e.winnerId(), "SYSTEM", e, e.at()));
        });
        handlers.on(AuctionClosedUnsold.class, (e, env) -> closeWith("CLOSED_UNSOLD", env, e.at())
                .then(logEvent(env, "CLOSED_UNSOLD", null, null, "SYSTEM", e, e.at())));
        handlers.on(AuctionCancelled.class, (e, env) -> closeWith("CANCELLED", env, e.at())
                .then(logEvent(env, "CANCELLED", null, e.actorId(), e.actorType(), e, e.at())));
        handlers.on(BidRetracted.class, (e, env) -> bidStatus(e.bidId(), "RETRACTED", null, null, e.at())
                .then(reactivate(env, e.newActiveBidId(), e.newCurrentPriceCents(), e.at()))
                .then(logEvent(env, "BID_RETRACTED", e.bidId(), e.bidderId(), "USER", e, e.at())));
        handlers.on(BidCancelled.class, (e, env) -> bidStatus(e.bidId(), "CANCELLED", e.reason(), e.adminId(), e.at())
                .then(reactivate(env, e.newActiveBidId(), e.newCurrentPriceCents(), e.at()))
                .then(logEvent(env, "BID_CANCELLED", e.bidId(), e.adminId(), "ADMIN", e, e.at())));
    }

    @Override
    public Mono<Void> reset() {
        return Flux.just("announce_auction_event", "announce_bid", "announce_auction")
                .concatMap(table -> db.sql("delete from " + table).then()).then();
    }

    // ------------------------------------------------------------------ helpers

    private Mono<Void> upsertAuction(AnnounceAuction row, EventEnvelope env, LocalDateTime at) {
        row.setLastEventId(env.eventId());
        row.setUpdateTime(at);
        return auctions.selectByPrimaryKey(row.getId()).hasElement()
                .flatMap(exists -> exists ? auctions.updateByPrimaryKeySelective(row) : auctions.insertSelective(row)).then();
    }

    private Mono<Void> patchAuction(AnnounceAuction patch, EventEnvelope env, LocalDateTime at) {
        patch.setLastEventId(env.eventId());
        patch.setUpdateTime(at);
        return auctions.updateByPrimaryKeySelective(patch).then();
    }

    private Mono<Void> closeWith(String status, EventEnvelope env, LocalDateTime at) {
        AnnounceAuction patch = new AnnounceAuction();
        patch.setId(idOf(env));
        patch.setStatus(status);
        patch.setClosedTime(at);
        return patchAuction(patch, env, at);
    }

    private Mono<Void> upsertBid(AnnounceBid bid) {
        return bids.selectByPrimaryKey(bid.getId()).hasElement()
                .flatMap(exists -> exists ? bids.updateByPrimaryKeySelective(bid) : bids.insertSelective(bid)).then();
    }

    private Mono<Void> bidStatus(String bidId, String status, String reason, Integer cancelledBy, LocalDateTime at) {
        AnnounceBid patch = new AnnounceBid();
        patch.setId(bidId);
        patch.setStatus(status);
        patch.setCancelReason(reason);
        patch.setCancelledBy(cancelledBy);
        patch.setUpdateTime(at);
        return bids.updateByPrimaryKeySelective(patch).then();
    }

    /** After a retract or cancel: the next highest bid is ACTIVE again and the auction's price and count follow. */
    private Mono<Void> reactivate(EventEnvelope env, String activeBidId, long currentPriceCents, LocalDateTime at) {
        Mono<Void> active = activeBidId == null ? Mono.empty() : bidStatus(activeBidId, "ACTIVE", null, null, at);
        AnnounceAuction patch = new AnnounceAuction();
        patch.setId(idOf(env));
        patch.setCurrentPrice(money(currentPriceCents));
        return active.then(patchAuction(patch, env, at))
                .then(db.sql("update announce_auction set bid_count = (select count(*) from announce_bid where auction_id = :id and status in ('ACTIVE','OUTBID') and deleted = 0) where id = :id")
                        .bind("id", idOf(env)).fetch().rowsUpdated()).then();
    }

    /** Appends to the log unless this source event was already logged (replay). */
    private Mono<Void> logEvent(EventEnvelope env, String type, String bidId, Integer actorId, String actorType, Object payload, LocalDateTime at) {
        AnnounceAuctionEventExample seen = new AnnounceAuctionEventExample();
        seen.or().andSourceEventIdEqualTo(env.eventId());
        return log.countByExample(seen).flatMap(n -> {
            if (n > 0) {
                return Mono.empty();
            }
            AnnounceAuctionEvent row = new AnnounceAuctionEvent();
            row.setAuctionId(idOf(env));
            row.setBidId(bidId);
            row.setEventType(type);
            row.setActorId(actorId);
            row.setActorType(actorType);
            row.setPayload(toJson(payload));
            row.setEventTime(at);
            row.setSourceEventId(env.eventId());
            return log.insertSelective(row).then();
        });
    }

    private String toJson(Object payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (Exception e) {
            return null;
        }
    }

    private static String idOf(EventEnvelope env) {
        return Subjects.idOf(env.subject());
    }

    private static BigDecimal money(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }
}
