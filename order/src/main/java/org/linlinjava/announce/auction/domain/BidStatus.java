package org.linlinjava.announce.auction.domain;

/** ACTIVE is the current highest bid; OUTBID was beaten; WON closed the auction; CANCELLED by staff; RETRACTED by the bidder. */
public enum BidStatus {
    ACTIVE, OUTBID, WON, CANCELLED, RETRACTED;

    /** Counts toward the current price. */
    public boolean isStanding() {
        return this == ACTIVE || this == OUTBID;
    }
}
