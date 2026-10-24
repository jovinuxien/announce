package org.linlinjava.announce.goods.api;

/** How a goods announce is sold. Same names as the domain enum. Serialised as its name; part of the public contract of announce-management. */
public enum SaleTypeV1 {
    FIXED_PRICE, AUCTION, GIVEAWAY
}
