package org.linlinjava.announce.auction.application;

import org.linlinjava.announce.auction.domain.Auction;
import org.linlinjava.announce.auction.domain.AuctionCommands.CancelAuction;
import org.linlinjava.announce.auction.domain.AuctionCommands.CancelBid;
import org.linlinjava.announce.auction.domain.AuctionCommands.CloseAuction;
import org.linlinjava.announce.auction.domain.AuctionCommands.OpenAuction;
import org.linlinjava.announce.auction.domain.AuctionCommands.PlaceBid;
import org.linlinjava.announce.auction.domain.AuctionCommands.RetractBid;
import org.linlinjava.announce.auction.domain.AuctionCommands.SnagIt;
import org.linlinjava.announce.auction.infrastructure.config.AuctionProperties;
import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.cqrs.command.ReactiveCommandRouter;
import org.linlinjava.announce.cqrs.observability.MetaDataKeys;
import org.linlinjava.announce.db.dao.reactive.AnnounceAuctionMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceAuctionSellerMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceBidMapper;
import org.linlinjava.announce.db.domain.AnnounceAuction;
import org.linlinjava.announce.db.domain.AnnounceAuctionExample;
import org.linlinjava.announce.db.domain.AnnounceBid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Use cases of the auction context. The rules live in the aggregate; this facade adds what the aggregate cannot
 * see (is the seller allowed to be paid), dispatches through the framework router and answers queries from the
 * read model ({@code announce_auction}, {@code announce_bid}), never from the event store.
 */
@Service
public class AuctionService {

    private final ReactiveCommandRouter router;
    private final AnnounceAuctionMapper auctions;
    private final AnnounceBidMapper bids;
    private final AnnounceAuctionSellerMapper sellers;
    private final AuctionProperties properties;
    private final Clock clock;

    public AuctionService(ReactiveCommandRouter router, AnnounceAuctionMapper auctions, AnnounceBidMapper bids,
                          AnnounceAuctionSellerMapper sellers, AuctionProperties properties, Clock clock) {
        this.router = router;
        this.auctions = auctions;
        this.bids = bids;
        this.sellers = sellers;
        this.properties = properties;
        this.clock = clock;
    }

    /** Opens the auction of a published AUCTION announce; refused with {@code seller-not-payable} until the seller's payout account is enabled. */
    public Mono<Auction> open(OpenAuction command, String commandId) {
        return checkSellerPayable(command.sellerId()).then(Mono.defer(() -> send(command, commandId)));
    }

    public Mono<Auction> placeBid(String announceId, Integer bidderId, long amountCents, Long maxAmountCents, String commandId) {
        String bidId = commandId == null || commandId.isBlank() ? UUID.randomUUID().toString() : UUID.nameUUIDFromBytes(("bid:" + commandId).getBytes()).toString();
        return send(new PlaceBid(announceId, bidId, bidderId, amountCents, maxAmountCents), commandId);
    }

    public Mono<Auction> snagIt(String announceId, Integer buyerId, String commandId) {
        return send(new SnagIt(announceId, buyerId), commandId);
    }

    public Mono<Auction> cancelBySeller(String announceId, Integer sellerId, String reason, String commandId) {
        return send(new CancelAuction(announceId, sellerId, "USER", reason), commandId);
    }

    public Mono<Auction> cancelByStaff(String announceId, Integer adminId, String reason, String commandId) {
        return send(new CancelAuction(announceId, adminId, "ADMIN", reason), commandId);
    }

    /** System: the announce is gone (withdrawn, expired); a no-op when the auction is not open. */
    public Mono<Auction> cancelBySystem(String announceId, String reason, String commandId) {
        return send(new CancelAuction(announceId, null, "SYSTEM", reason), commandId);
    }

    public Mono<Auction> retractBid(String announceId, String bidId, Integer bidderId, String commandId) {
        return send(new RetractBid(announceId, bidId, bidderId), commandId);
    }

    /** Staff: {@code bidId} alone identifies the bid; the auction is looked up in the read model. */
    public Mono<Auction> cancelBid(String bidId, Integer adminId, String reason, String commandId) {
        return bids.selectByPrimaryKey(bidId)
                .switchIfEmpty(Mono.error(() -> new CommandRejectedException(HttpStatus.NOT_FOUND, "bid-unknown", "No bid " + bidId)))
                .flatMap(bid -> send(new CancelBid(bid.getAuctionId(), bidId, adminId, reason), commandId));
    }

    /** Closes every open auction whose end has passed (read-model lookup, one command per auction). Returns how many were sent. */
    public Mono<Long> closeDue(int batchSize) {
        AnnounceAuctionExample due = new AnnounceAuctionExample();
        due.or().andStatusEqualTo("OPEN").andEndTimeLessThanOrEqualTo(LocalDateTime.now(clock)).andDeletedEqualTo(false);
        due.setOrderByClause("end_time asc");
        return auctions.selectPageByExample(due, 0, batchSize)
                .flatMap(a -> send(new CloseAuction(a.getId()), null), 4)
                .count();
    }

    // ------------------------------------------------------------------ queries (read model)

    public Mono<AnnounceAuction> findAuction(String announceId) {
        return auctions.selectByPrimaryKey(announceId).filter(a -> !Boolean.TRUE.equals(a.getDeleted()));
    }

    /** Staff listing filter: every field optional; {@code q} matches the announce title. */
    public record AuctionFilter(String status, Integer sellerId, String q) {
        org.linlinjava.announce.db.domain.AnnounceAuctionExample toExample() {
            var example = new org.linlinjava.announce.db.domain.AnnounceAuctionExample();
            var c = example.or().andDeletedEqualTo(false);
            if (status != null && !status.isBlank()) c.andStatusEqualTo(status.trim().toUpperCase(java.util.Locale.ROOT));
            if (sellerId != null) c.andSellerIdEqualTo(sellerId);
            if (q != null && !q.isBlank()) c.andTitleLike("%" + q.trim() + "%");
            example.setOrderByClause("end_time desc, id");
            return example;
        }
    }

    public Mono<Long> countAuctions(AuctionFilter filter) {
        return auctions.countByExample(filter.toExample());
    }

    public Flux<AnnounceAuction> findAuctions(AuctionFilter filter, long offset, int limit) {
        return auctions.selectPageByExample(filter.toExample(), offset, limit);
    }

    public Flux<AnnounceBid> findBids(String announceId) {
        var example = new org.linlinjava.announce.db.domain.AnnounceBidExample();
        example.or().andAuctionIdEqualTo(announceId).andDeletedEqualTo(false);
        example.setOrderByClause("amount desc, bid_time asc");
        return bids.selectByExample(example);
    }

    public AuctionProperties properties() {
        return properties;
    }

    // ------------------------------------------------------------------ helpers

    private Mono<Void> checkSellerPayable(Integer sellerId) {
        if (!properties.getSellerAccount().isRequired()) {
            return Mono.empty();
        }
        return sellers.selectByPrimaryKey(sellerId)
                .filter(s -> Boolean.TRUE.equals(s.getPayoutsEnabled()))
                .switchIfEmpty(Mono.error(() -> new CommandRejectedException(HttpStatus.CONFLICT, "seller-not-payable",
                        "Seller " + sellerId + " has no enabled payout account; the auction cannot be opened")))
                .then();
    }

    private Mono<Auction> send(com.opencqrs.framework.command.Command command, String commandId) {
        Map<String, ?> metaData = commandId == null || commandId.isBlank() ? Map.of() : Map.of(MetaDataKeys.COMMAND_ID, commandId);
        return router.send(command, metaData, Auction.class);
    }
}
