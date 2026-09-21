package org.linlinjava.announce.goods.domain.announce.events;

import org.linlinjava.announce.goods.domain.announce.AnnounceId;
import org.linlinjava.announce.goods.domain.announce.SellerId;

/** Stock of a variant moved; {@code stock == 0} means the variant is sold out (order context reacts by refusing new lines). */
public final class StockAdjusted extends AnnounceEvent {
    private final Integer variantId;
    private final int delta;
    private final int stock;

    public StockAdjusted(AnnounceId announceId, SellerId sellerId, Integer variantId, int delta, int stock) {
        super(announceId, sellerId);
        this.variantId = variantId;
        this.delta = delta;
        this.stock = stock;
    }

    public Integer variantId() { return variantId; }
    public int delta() { return delta; }
    public int stock() { return stock; }
    public boolean soldOut() { return stock == 0; }
}
