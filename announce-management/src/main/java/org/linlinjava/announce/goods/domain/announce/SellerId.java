package org.linlinjava.announce.goods.domain.announce;

/** The member who owns an announce (announce_user.id). Referenced by identity only: members live in their own context. */
public record SellerId(Integer value) {
    public SellerId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("Seller id must be a positive integer");
        }
    }
}
