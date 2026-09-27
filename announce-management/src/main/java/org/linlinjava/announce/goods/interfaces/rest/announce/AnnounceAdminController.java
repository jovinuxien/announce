package org.linlinjava.announce.goods.interfaces.rest.announce;

import org.linlinjava.announce.goods.application.search.AnnounceReindexService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Staff triggers on the announces, reached through the admin edge only (no owner header). Today: re-push every
 * PUBLISHED announce to the search index after an index rebuild (the seed rows have no events, so this is the only
 * way they get indexed). The moderation endpoints (list, withdraw, expire) belong to the gateway-admin work.
 */
@RestController
@RequestMapping("/api/admin/announces")
public class AnnounceAdminController {

    private final AnnounceReindexService reindex;

    public AnnounceAdminController(AnnounceReindexService reindex) {
        this.reindex = reindex;
    }

    @PostMapping("/reindex")
    public Mono<Map<String, Long>> reindex() {
        return reindex.reindexPublished().map(n -> Map.of("indexed", n));
    }
}
