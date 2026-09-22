package org.linlinjava.announce.goods.application.search;

import reactor.core.publisher.Mono;

/** Port to the search index: one document per published announce of any kind, removed when it is no longer visible. */
public interface AnnounceIndexer {

    Mono<Void> index(AnnounceDocument document);

    Mono<Void> remove(String announceId);
}
