package org.linlinjava.announce.auction.domain;

import java.time.LocalDateTime;

/** One bid as the aggregate remembers it. */
public record Bid(String id, Integer bidderId, long amountCents, Long maxAmountCents, LocalDateTime at, BidStatus status) {

    public Bid withStatus(BidStatus newStatus) {
        return new Bid(id, bidderId, amountCents, maxAmountCents, at, newStatus);
    }
}
