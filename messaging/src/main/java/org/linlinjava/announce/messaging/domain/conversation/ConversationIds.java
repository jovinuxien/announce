package org.linlinjava.announce.messaging.domain.conversation;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Id rules: a LISTING conversation has one stable id per (announce, buyer); everything else is a random UUID. */
public final class ConversationIds {

    private ConversationIds() {
    }

    /** The one conversation between this buyer and the seller of this announce. */
    public static String listing(String announceId, Integer buyerId) {
        return UUID.nameUUIDFromBytes(("listing:" + announceId + ":" + buyerId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public static String support() {
        return UUID.randomUUID().toString();
    }

    public static String message() {
        return UUID.randomUUID().toString();
    }
}
