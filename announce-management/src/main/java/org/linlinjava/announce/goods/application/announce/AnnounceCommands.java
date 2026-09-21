package org.linlinjava.announce.goods.application.announce;

import org.linlinjava.announce.goods.domain.announce.AnnounceDetails;
import org.linlinjava.announce.goods.domain.announce.AnnounceId;
import org.linlinjava.announce.goods.domain.announce.Money;
import org.linlinjava.announce.goods.domain.announce.SellerId;

import java.time.Duration;
import java.util.List;

/** Commands of the announce use cases. Every write carries the seller so ownership can be enforced. */
public final class AnnounceCommands {
    private AnnounceCommands() {}

    public record DraftAnnounce(SellerId sellerId, AnnounceDetails details) {}

    /** {@code validity} null means the default (60 days). */
    public record PublishAnnounce(AnnounceId announceId, SellerId sellerId, Duration validity) {}

    public record EditAnnounce(AnnounceId announceId, SellerId sellerId, AnnounceDetails details) {}

    public record ChangeAnnouncePrice(AnnounceId announceId, SellerId sellerId, Money price) {}

    public record WithdrawAnnounce(AnnounceId announceId, SellerId sellerId) {}

    public record MarkAnnounceSold(AnnounceId announceId, SellerId sellerId) {}

    public record AddVariant(AnnounceId announceId, SellerId sellerId, List<String> specifications, Money price, int stock, String pictureUrl) {}

    public record AdjustStock(AnnounceId announceId, SellerId sellerId, Integer variantId, int delta) {}
}
