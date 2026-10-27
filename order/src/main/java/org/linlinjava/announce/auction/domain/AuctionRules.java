package org.linlinjava.announce.auction.domain;

import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

/**
 * The invariants of the Auction aggregate ({@code doc/auction-chat-schema.md} §3), each answered with an RFC 7807
 * problem carrying a stable {@code code}:
 * <ul>
 *   <li>a bid needs an OPEN auction inside its window, a bidder who is not the seller, and
 *       {@code amount >= currentPrice + increment} (the start price for the first bid)</li>
 *   <li>snag-it only while no bid exists and a snag-it price is set</li>
 *   <li>the seller cancels only before the first bid; staff any time</li>
 *   <li>closing needs the end to have passed; the reserve decides won or unsold</li>
 *   <li>the window moves only while the auction is open and no bid stands</li>
 * </ul>
 */
public final class AuctionRules {
    private AuctionRules() {}

    public static void checkOpen(AuctionCommands.OpenAuction c) {
        if (c.sellerId() == null || c.sellerId() <= 0) throw new IllegalArgumentException("The seller is required");
        if (c.initialPriceCents() < 0) throw new IllegalArgumentException("The start price must not be negative");
        if (c.incrementCents() <= 0) throw new IllegalArgumentException("The bid increment must be positive");
        if (c.startsAt() == null || c.endsAt() == null || !c.startsAt().isBefore(c.endsAt())) {
            throw reject("auction-window", "An auction must start before it ends");
        }
        if (c.reservePriceCents() != null && c.reservePriceCents() < c.initialPriceCents()) {
            throw reject("reserve-below-start", "The reserve price cannot be below the start price");
        }
        if (c.snagItPriceCents() != null && c.snagItPriceCents() <= c.initialPriceCents()) {
            throw reject("snag-it-below-start", "The snag-it price must be above the start price");
        }
    }

    public static void checkReschedule(Auction a, AuctionCommands.RescheduleAuction c) {
        if (!a.status().isOpen()) throw reject("auction-not-open", "The auction is " + a.status());
        if (a.hasBids()) throw reject("bids-exist", "An auction with bids keeps its window");
        if (c.startsAt() == null || c.endsAt() == null || !c.startsAt().isBefore(c.endsAt())) {
            throw reject("auction-window", "An auction must start before it ends");
        }
    }

    public static void checkBid(Auction a, AuctionCommands.PlaceBid c, LocalDateTime now) {
        checkOpenWindow(a, now);
        if (c.bidderId() == null || c.bidderId().equals(a.sellerId())) {
            throw reject("seller-cannot-bid", "The seller cannot bid on their own auction");
        }
        if (a.bid(c.bidId()).isPresent()) {
            throw reject("duplicate-bid", "Bid " + c.bidId() + " was already placed");
        }
        long minimum = a.minimumNextBidCents();
        if (c.amountCents() < minimum) {
            throw reject("bid-below-minimum", "The bid must be at least " + minimum + " minor units (current price " + a.currentPriceCents()
                    + (a.hasBids() ? " + increment " + a.incrementCents() : "") + ")");
        }
        if (c.maxAmountCents() != null && c.maxAmountCents() < c.amountCents()) {
            throw reject("max-below-bid", "The proxy maximum cannot be below the bid");
        }
    }

    public static void checkSnag(Auction a, AuctionCommands.SnagIt c, LocalDateTime now) {
        checkOpenWindow(a, now);
        if (c.buyerId() == null || c.buyerId().equals(a.sellerId())) {
            throw reject("seller-cannot-bid", "The seller cannot buy their own item");
        }
        if (a.snagItPriceCents() == null) {
            throw reject("no-snag-it-price", "This auction has no snag-it price");
        }
        if (a.hasBids()) {
            throw reject("bids-exist", "Snag-it is only possible while no bid has been placed");
        }
    }

    /** True when the closing job must act: open and the end has passed. */
    public static boolean isDueToClose(Auction a, LocalDateTime now) {
        return a.status().isOpen() && !a.endsAt().isAfter(now);
    }

    public static boolean meetsReserve(Auction a, Bid highest) {
        return a.reservePriceCents() == null || highest.amountCents() >= a.reservePriceCents();
    }

    public static void checkCancel(Auction a, AuctionCommands.CancelAuction c) {
        if (!a.status().isOpen()) {
            throw reject("auction-not-open", "The auction is " + a.status());
        }
        switch (c.actorType()) {
            case "ADMIN", "SYSTEM" -> { }
            case "USER" -> {
                if (!a.sellerId().equals(c.actorId())) {
                    throw new CommandRejectedException(HttpStatus.FORBIDDEN, "not-owner", "Only the seller can cancel the auction");
                }
                if (a.hasBids()) {
                    throw reject("bids-exist", "An auction with bids can only be cancelled by staff");
                }
            }
            default -> throw new IllegalArgumentException("Unknown actor type " + c.actorType());
        }
    }

    public static Bid checkRetract(Auction a, AuctionCommands.RetractBid c) {
        if (!a.status().isOpen()) throw reject("auction-not-open", "The auction is " + a.status());
        Bid bid = a.bid(c.bidId()).orElseThrow(() -> reject("bid-unknown", "No bid " + c.bidId()));
        if (!bid.bidderId().equals(c.bidderId())) {
            throw new CommandRejectedException(HttpStatus.FORBIDDEN, "not-bidder", "Only the bidder can retract a bid");
        }
        if (!bid.status().isStanding()) throw reject("bid-not-standing", "Bid " + c.bidId() + " is " + bid.status());
        return bid;
    }

    public static Bid checkCancelBid(Auction a, AuctionCommands.CancelBid c) {
        if (!a.status().isOpen()) throw reject("auction-not-open", "The auction is " + a.status());
        Bid bid = a.bid(c.bidId()).orElseThrow(() -> reject("bid-unknown", "No bid " + c.bidId()));
        if (!bid.status().isStanding()) throw reject("bid-not-standing", "Bid " + c.bidId() + " is " + bid.status());
        return bid;
    }

    private static void checkOpenWindow(Auction a, LocalDateTime now) {
        if (!a.status().isOpen()) throw reject("auction-not-open", "The auction is " + a.status());
        if (now.isBefore(a.startsAt())) throw reject("auction-not-started", "The auction starts at " + a.startsAt());
        if (!now.isBefore(a.endsAt())) throw reject("auction-ended", "The auction ended at " + a.endsAt());
    }

    private static CommandRejectedException reject(String code, String detail) {
        return new CommandRejectedException(HttpStatus.CONFLICT, code, detail);
    }
}
