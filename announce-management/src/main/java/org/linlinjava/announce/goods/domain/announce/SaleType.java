package org.linlinjava.announce.goods.domain.announce;

/** How an announce is sold. AUCTION delegates price discovery to the auction context; GIVEAWAY has no price. */
public enum SaleType {
    FIXED_PRICE, AUCTION, GIVEAWAY
}
