package org.linlinjava.announce.goods.application.announce;

import org.linlinjava.announce.goods.domain.announce.AnnounceId;
import org.linlinjava.announce.goods.domain.announce.SellerId;

public class NotAnnounceOwnerException extends RuntimeException {
    public NotAnnounceOwnerException(AnnounceId id, SellerId sellerId) {
        super("Announce " + id.value() + " does not belong to member " + sellerId.value());
    }
}
