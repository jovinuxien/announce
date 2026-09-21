package org.linlinjava.announce.goods.domain.announce;

/** Lifecycle of an announce: DRAFT -> PUBLISHED -> SOLD | WITHDRAWN | EXPIRED. Withdrawn and expired announces can be republished. */
public enum AnnounceStatus {
    DRAFT, PUBLISHED, SOLD, WITHDRAWN, EXPIRED;

    public boolean canBePublished() {
        return this == DRAFT || this == WITHDRAWN || this == EXPIRED;
    }

    public boolean isPublished() {
        return this == PUBLISHED;
    }
}
