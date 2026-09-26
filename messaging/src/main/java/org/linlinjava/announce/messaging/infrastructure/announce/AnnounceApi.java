package org.linlinjava.announce.messaging.infrastructure.announce;

import org.linlinjava.announce.messaging.application.AnnounceRef;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import reactor.core.publisher.Mono;

/**
 * The part of the announce-management REST API this context calls. Until an {@code announce-management-api}
 * module publishes the descriptor, the interface is declared here against {@code GET /api/announces/{id}};
 * unknown response fields are ignored.
 */
@HttpExchange("/api/announces")
public interface AnnounceApi {

    @GetExchange("/{id}")
    Mono<AnnounceRef> get(@PathVariable String id);
}
