package org.linlinjava.announce.search;

import org.linlinjava.announce.cqrs.api.HttpExchangeClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * {@link SearchIndexer} over the OCS indexer, reached through the framework's service client with the fixed base
 * url {@code cqrs.api.clients.ocs-indexer.base-url} (the search tier is outside the registry). Index name from
 * {@code announce.search.index}. Active when {@code announce.search.mode} is {@code ocs} (the default); tests use
 * {@code noop}, which installs the {@link RecordingSearchIndexer}.
 */
@Component
@ConditionalOnProperty(prefix = "announce.search", name = "mode", havingValue = "ocs", matchIfMissing = true)
public class OcsSearchIndexer implements SearchIndexer {

    public static final String SERVICE_ID = "ocs-indexer";

    private final OcsIndexerApi api;
    private final String index;

    public OcsSearchIndexer(HttpExchangeClients clients, @Value("${announce.search.index:announce_index}") String index) {
        this.api = clients.create(OcsIndexerApi.class, SERVICE_ID);
        this.index = index;
    }

    @Override
    public Mono<Void> index(String id, Map<String, Object> data) {
        return api.putDocuments(index, List.of(new OcsIndexerApi.Document(id, data)));
    }

    @Override
    public Mono<Void> remove(String id) {
        return api.deleteDocuments(index, List.of(id));
    }
}
