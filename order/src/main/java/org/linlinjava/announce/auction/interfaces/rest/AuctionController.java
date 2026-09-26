package org.linlinjava.announce.auction.interfaces.rest;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.linlinjava.announce.auction.application.AuctionService;
import org.linlinjava.announce.auction.domain.Auction;
import org.linlinjava.announce.db.domain.AnnounceAuction;
import org.linlinjava.announce.db.domain.AnnounceBid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * {@code /api/auctions/{announceId}}: reads come from the read model, writes are commands and answer with the
 * aggregate state the command produced. The member comes from {@code X-User-Id} (set by the gateway), staff from
 * {@code X-Admin-Id}; {@code X-Command-Id} makes a write idempotent and also fixes the bid id of a retried bid.
 */
@RestController
@Validated
public class AuctionController {

    public static final String USER_HEADER = "X-User-Id";
    public static final String ADMIN_HEADER = "X-Admin-Id";
    public static final String COMMAND_ID_HEADER = "X-Command-Id";

    private final AuctionService auctions;

    public AuctionController(AuctionService auctions) {
        this.auctions = auctions;
    }

    public record BidRequest(@NotNull @Positive Long amountCents, Long maxAmountCents) {}
    public record ReasonRequest(String reason) {}
    public record AuctionView(AnnounceAuction auction, List<AnnounceBid> bids) {}

    @GetMapping("/api/auctions/{announceId}")
    public Mono<AuctionView> get(@PathVariable String announceId) {
        return auctions.findAuction(announceId)
                .switchIfEmpty(Mono.error(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No auction for announce " + announceId)))
                .flatMap(a -> auctions.findBids(announceId).collectList().map(b -> new AuctionView(a, b)));
    }

    @PostMapping("/api/auctions/{announceId}/bids")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Auction> bid(@PathVariable String announceId, @RequestHeader(USER_HEADER) Integer user,
                             @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId, @RequestBody BidRequest body) {
        return auctions.placeBid(announceId, user, body.amountCents(), body.maxAmountCents(), commandId);
    }

    @PostMapping("/api/auctions/{announceId}/snag")
    public Mono<Auction> snag(@PathVariable String announceId, @RequestHeader(USER_HEADER) Integer user,
                              @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId) {
        return auctions.snagIt(announceId, user, commandId);
    }

    @PostMapping("/api/auctions/{announceId}/cancel")
    public Mono<Auction> cancel(@PathVariable String announceId, @RequestHeader(USER_HEADER) Integer user,
                                @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId,
                                @RequestBody(required = false) ReasonRequest body) {
        return auctions.cancelBySeller(announceId, user, body == null ? null : body.reason(), commandId);
    }

    @DeleteMapping("/api/auctions/{announceId}/bids/{bidId}")
    public Mono<Auction> retract(@PathVariable String announceId, @PathVariable String bidId, @RequestHeader(USER_HEADER) Integer user,
                                 @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId) {
        return auctions.retractBid(announceId, bidId, user, commandId);
    }

    @PostMapping("/api/admin/bids/{bidId}/cancel")
    public Mono<Auction> cancelBid(@PathVariable String bidId, @RequestHeader(ADMIN_HEADER) Integer admin,
                                   @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId,
                                   @RequestBody(required = false) ReasonRequest body) {
        return auctions.cancelBid(bidId, admin, body == null ? null : body.reason(), commandId);
    }

    @PostMapping("/api/admin/auctions/{announceId}/cancel")
    public Mono<Auction> cancelByStaff(@PathVariable String announceId, @RequestHeader(ADMIN_HEADER) Integer admin,
                                       @RequestHeader(value = COMMAND_ID_HEADER, required = false) String commandId,
                                       @RequestBody(required = false) ReasonRequest body) {
        return auctions.cancelByStaff(announceId, admin, body == null ? null : body.reason(), commandId);
    }

    /** Runs the closing job once; for operators and tests. */
    @PostMapping("/api/admin/auctions/close-due")
    public Mono<Map<String, Long>> closeDue(@RequestHeader(ADMIN_HEADER) Integer admin) {
        return auctions.closeDue(200).map(n -> Map.of("closed", n));
    }

}
