package org.linlinjava.announce.goods.api;

/** Lifecycle of an announce as the API shows it: DRAFT -> PUBLISHED -> SOLD, WITHDRAWN or EXPIRED. Same names as the domain enum. Serialised as its name; part of the public contract of announce-management. */
public enum AnnounceStatusV1 {
    DRAFT, PUBLISHED, SOLD, WITHDRAWN, EXPIRED
}
