package org.linlinjava.announce.goods.domain.announce.events;

import org.linlinjava.announce.goods.domain.announce.AnnounceId;
import org.linlinjava.announce.goods.domain.announce.Money;
import org.linlinjava.announce.goods.domain.announce.SellerId;

public final class AnnouncePriceChanged extends AnnounceEvent {
    private final Money previous;
    private final Money current;

    public AnnouncePriceChanged(AnnounceId announceId, SellerId sellerId, Money previous, Money current) {
        super(announceId, sellerId);
        this.previous = previous;
        this.current = current;
    }

    public Money previous() { return previous; }
    public Money current() { return current; }
}
