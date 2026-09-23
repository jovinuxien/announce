package org.linlinjava.announce.goods.infrastructure.search;

import org.linlinjava.announce.cqrs.api.HttpExchangeClients;
import org.linlinjava.announce.goods.application.search.AnnounceDocument;
import org.linlinjava.announce.goods.application.search.AnnounceIndexer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * {@link AnnounceIndexer} over the OCS indexer, reached through the framework's service client with the fixed base
 * url {@code cqrs.api.clients.ocs-indexer.base-url} (the search tier is outside the registry). Index name from
 * {@code announce.search.index}. Switch off with {@code announce.search.mode} other than {@code ocs} (tests).
 */
@Component
@ConditionalOnProperty(prefix = "announce.search", name = "mode", havingValue = "ocs", matchIfMissing = true)
public class OcsAnnounceIndexer implements AnnounceIndexer {

    public static final String SERVICE_ID = "ocs-indexer";

    private final OcsIndexerApi api;
    private final String index;

    public OcsAnnounceIndexer(HttpExchangeClients clients, @Value("${announce.search.index:announce_index}") String index) {
        this.api = clients.create(OcsIndexerApi.class, SERVICE_ID);
        this.index = index;
    }

    @Override
    public Mono<Void> index(AnnounceDocument document) {
        return api.putDocuments(index, List.of(new OcsIndexerApi.Document(document.id(), document.data())));
    }

    @Override
    public Mono<Void> remove(String announceId) {
        return api.deleteDocuments(index, List.of(announceId));
    }
}
