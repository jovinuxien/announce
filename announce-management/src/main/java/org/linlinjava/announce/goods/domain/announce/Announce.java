package org.linlinjava.announce.goods.domain.announce;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceEdited;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceExpired;
import org.linlinjava.announce.goods.domain.announce.events.AnnouncePosted;
import org.linlinjava.announce.goods.domain.announce.events.AnnouncePublished;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceSold;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceWithdrawn;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * State of the Announce aggregate under {@code /announce/{id}}, rebuilt from its events. A generic announce of one
 * {@link Kind}: what every kind shares lives in {@link AnnounceContent}, the kind-specific part in the sealed
 * {@link AnnounceKind}. Immutable; the {@code apply} methods are the state rebuilding functions and the rules that
 * guard each command live in {@link AnnounceRules}.
 *
 * <p>Lifecycle: DRAFT -> PUBLISHED -> SOLD (goods only) | WITHDRAWN | EXPIRED; withdrawn and expired announces can
 * be published again.
 */
public record Announce(String id,
                       SellerId sellerId,
                       AnnounceContent content,
                       AnnounceKind details,
                       AnnounceStatus status,
                       LocalDateTime publishedAt,
                       LocalDateTime expiresAt,
                       LocalDateTime createdAt,
                       LocalDateTime updatedAt) {

    /** Blocket keeps an ad for 60 days by default. */
    public static final Duration DEFAULT_VALIDITY = Duration.ofDays(60);

    public Kind kind() {
        return details.kind();
    }

    public boolean isOwnedBy(SellerId candidate) {
        return sellerId.equals(candidate);
    }

    @JsonIgnore
    public boolean isVisible() {
        return status.isPublished();
    }

    // ------------------------------------------------------------------ state rebuilding

    public static Announce apply(String id, AnnouncePosted event) {
        return new Announce(id, event.sellerId(), event.content(), event.details(), AnnounceStatus.DRAFT, null, null, event.at(), event.at());
    }

    public Announce apply(AnnounceEdited event) {
        return new Announce(id, sellerId, event.content(), event.details(), status, publishedAt, expiresAt, createdAt, event.at());
    }

    public Announce apply(AnnouncePublished event) {
        return new Announce(id, sellerId, content, details, AnnounceStatus.PUBLISHED, event.at(), event.expiresAt(), createdAt, event.at());
    }

    public Announce apply(AnnounceWithdrawn event) {
        return new Announce(id, sellerId, content, details, AnnounceStatus.WITHDRAWN, publishedAt, expiresAt, createdAt, event.at());
    }

    public Announce apply(AnnounceSold event) {
        return new Announce(id, sellerId, content, details, AnnounceStatus.SOLD, publishedAt, expiresAt, createdAt, event.at());
    }

    public Announce apply(AnnounceExpired event) {
        return new Announce(id, sellerId, content, details, AnnounceStatus.EXPIRED, publishedAt, expiresAt, createdAt, event.at());
    }
}
