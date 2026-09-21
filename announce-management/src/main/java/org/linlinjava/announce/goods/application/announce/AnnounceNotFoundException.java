package org.linlinjava.announce.goods.application.announce;

import org.linlinjava.announce.goods.domain.announce.AnnounceId;

public class AnnounceNotFoundException extends RuntimeException {
    public AnnounceNotFoundException(AnnounceId id) {
        super("Announce " + id.value() + " does not exist");
    }
}
