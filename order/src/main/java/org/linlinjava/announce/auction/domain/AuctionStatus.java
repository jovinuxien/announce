package org.linlinjava.announce.auction.domain;

/** OPEN until the end passes or a snag-it; then one of the closed states. */
public enum AuctionStatus {
    OPEN, CLOSED_WON, CLOSED_UNSOLD, CLOSED_SNAGGED, CANCELLED;

    public boolean isOpen() {
        return this == OPEN;
    }
}
