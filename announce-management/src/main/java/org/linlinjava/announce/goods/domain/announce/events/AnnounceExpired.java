package org.linlinjava.announce.goods.domain.announce.events;

import org.linlinjava.announce.goods.domain.announce.AnnounceId;
import org.linlinjava.announce.goods.domain.announce.SellerId;

public final class AnnounceExpired extends AnnounceEvent {
    public AnnounceExpired(AnnounceId announceId, SellerId sellerId) {
        super(announceId, sellerId);
    }
}
