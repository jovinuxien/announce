package org.linlinjava.announce.goods.infrastructure.search;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PutExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * The partial-update API of the OCS indexer service ({@code docker/cqrs}: {@code commerceexperts/ocs-indexer-service}
 * on port 8535). Documents are {@code {"id": ..., "data": {...}}}; the index must exist (the field configuration in
 * {@code application.indexer-service.yml} creates it on the first full import or update).
 */
@HttpExchange("/indexer-api/v1/update")
public interface OcsIndexerApi {

    record Document(String id, Map<String, Object> data) {
    }

    @PutExchange("/{indexName}")
    Mono<Void> putDocuments(@PathVariable String indexName, @RequestBody List<Document> documents);

    @DeleteExchange("/{indexName}")
    Mono<Void> deleteDocuments(@PathVariable String indexName, @RequestParam("id") List<String> ids);
}
