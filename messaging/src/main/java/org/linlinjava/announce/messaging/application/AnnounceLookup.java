package org.linlinjava.announce.messaging.application;

import reactor.core.publisher.Mono;

/**
 * Port to the announce context: the owner of an announce is needed to open a listing conversation. The default
 * implementation calls the announce-management API; a later one can read a snapshot fed from {@code announce.v1}.
 */
public interface AnnounceLookup {

    /** The announce, or empty when it does not exist. */
    Mono<AnnounceRef> find(String announceId);
}
