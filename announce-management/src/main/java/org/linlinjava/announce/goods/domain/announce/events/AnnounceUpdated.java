package org.linlinjava.announce.goods.domain.announce.events;

import org.linlinjava.announce.goods.domain.announce.AnnounceId;
import org.linlinjava.announce.goods.domain.announce.SellerId;

public final class AnnounceUpdated extends AnnounceEvent {
    public AnnounceUpdated(AnnounceId announceId, SellerId sellerId) {
        super(announceId, sellerId);
    }
}
