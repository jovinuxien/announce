package org.linlinjava.announce.messaging.application;

import reactor.core.publisher.Mono;

/**
 * Port to the announce context: the owner of an announce is needed to open a listing conversation. The default
 * implementation calls announce-management through its published descriptor
 * ({@code org.linlinjava.announce.goods.api.AnnounceApi}); a later one can read a snapshot fed from {@code announce.v1}.
 */
public interface AnnounceLookup {

    /** What this context needs to know about an announce: who owns it, and enough to name it. */
    record AnnounceFacts(String id, Integer sellerId, String title, String status) {
    }

    /** The announce, or empty when it does not exist (or is not visible to a service without a member identity). */
    Mono<AnnounceFacts> find(String announceId);
}
