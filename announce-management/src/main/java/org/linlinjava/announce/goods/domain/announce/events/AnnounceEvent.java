package org.linlinjava.announce.goods.domain.announce.events;

import org.linlinjava.announce.goods.domain.announce.AnnounceId;
import org.linlinjava.announce.goods.domain.announce.SellerId;
import org.linlinjava.announce.goods.domain.model.events.LitemallDomainEvent;

/** Base of every event the Announce aggregate publishes. Other contexts subscribe by announce id. */
public abstract class AnnounceEvent extends LitemallDomainEvent {

    private final AnnounceId announceId;
    private final SellerId sellerId;

    protected AnnounceEvent(AnnounceId announceId, SellerId sellerId) {
        this.announceId = announceId;
        this.sellerId = sellerId;
    }

    public AnnounceId announceId() {
        return announceId;
    }

    public SellerId sellerId() {
        return sellerId;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[announce=" + announceId.value() + ", seller=" + sellerId.value() + "]";
    }
}
