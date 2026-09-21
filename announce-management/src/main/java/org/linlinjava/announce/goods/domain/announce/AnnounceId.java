package org.linlinjava.announce.goods.domain.announce;

/** Identity of an announce (announce_goods.id). */
public record AnnounceId(Integer value) {
    public AnnounceId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("Announce id must be a positive integer");
        }
    }
}
