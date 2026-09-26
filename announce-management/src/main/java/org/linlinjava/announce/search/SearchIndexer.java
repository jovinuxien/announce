package org.linlinjava.announce.search;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Port to the search index shared by every sub-context of this service: one flat document per visible item, keyed
 * by an id that is stable for the item (an announce id, or {@code {source}-{external_id}} for a crawled listing).
 * Field names follow {@code docker/cqrs/application.indexer-service.yml}; {@code source} tells the origins apart.
 */
public interface SearchIndexer {

    Mono<Void> index(String id, Map<String, Object> data);

    Mono<Void> remove(String id);
}
