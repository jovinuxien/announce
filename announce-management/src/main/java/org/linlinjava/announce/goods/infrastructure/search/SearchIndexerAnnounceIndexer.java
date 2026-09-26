package org.linlinjava.announce.goods.infrastructure.search;

import org.linlinjava.announce.goods.application.search.AnnounceDocument;
import org.linlinjava.announce.goods.application.search.AnnounceIndexer;
import org.linlinjava.announce.search.SearchIndexer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * {@link AnnounceIndexer} that delegates to the shared {@link SearchIndexer} (OCS in production, a recording stand-in
 * under {@code announce.search.mode=noop}); announces and crawled listings share one index and one client.
 */
@Component
public class SearchIndexerAnnounceIndexer implements AnnounceIndexer {

    private final SearchIndexer indexer;

    public SearchIndexerAnnounceIndexer(SearchIndexer indexer) {
        this.indexer = indexer;
    }

    @Override
    public Mono<Void> index(AnnounceDocument document) {
        return indexer.index(document.id(), document.data());
    }

    @Override
    public Mono<Void> remove(String announceId) {
        return indexer.remove(announceId);
    }
}
