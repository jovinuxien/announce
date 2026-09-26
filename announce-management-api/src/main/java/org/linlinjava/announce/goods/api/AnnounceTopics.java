package org.linlinjava.announce.goods.api;

/** Kafka topics of announce-management. Consumers bind {@code <function>-in-0.destination} to these names. */
public final class AnnounceTopics {
    /** Every lifecycle change of an announce, keyed by subject {@code /announce/{id}}. */
    public static final String ANNOUNCE_V1 = "announce.v1";

    private AnnounceTopics() {}
}
