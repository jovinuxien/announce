package org.linlinjava.announce.listing.interfaces.rest;

import org.linlinjava.announce.listing.application.ImportReport;
import org.linlinjava.announce.listing.application.ListingImportService;
import org.linlinjava.announce.listing.application.ListingInventory;
import org.linlinjava.announce.listing.application.ListingInventoryService;
import org.linlinjava.announce.listing.application.ListingLivenessService;
import org.linlinjava.announce.listing.application.ListingReindexService;
import org.linlinjava.announce.listing.application.LivenessReport;
import org.linlinjava.announce.listing.domain.ListingSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Admin triggers for the crawled listings: import one feed file by path (the source is taken from the body or,
 * when absent, from the file name prefix), re-push every indexable ACTIVE listing to the index after a rebuild, run
 * one liveness batch now, and the inventory (counts per source, status and grade, last import).
 */
@RestController
@RequestMapping("/api/admin/listings")
public class ListingAdminController {

    /** {@code {"path": "/.../dba_202410081230.jsonl", "source": "DBA"}}; source optional. */
    public record ImportRequest(String path, ListingSource source) {
    }

    private final ListingImportService importer;
    private final ListingReindexService reindex;
    private final ListingLivenessService liveness;
    private final ListingInventoryService inventory;

    public ListingAdminController(ListingImportService importer, ListingReindexService reindex,
                                  ListingLivenessService liveness, ListingInventoryService inventory) {
        this.importer = importer;
        this.reindex = reindex;
        this.liveness = liveness;
        this.inventory = inventory;
    }

    @PostMapping("/import")
    public Mono<ImportReport> importFile(@RequestBody ImportRequest request) {
        if (request.path() == null || request.path().isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        Path file = Path.of(request.path());
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("not a readable file: " + file);
        }
        ListingSource source = request.source() != null ? request.source() : ListingSource.ofPrefix(file.getFileName().toString());
        return importer.importFile(source, file);
    }

    @PostMapping("/reindex")
    public Mono<Map<String, Long>> reindex() {
        return reindex.reindexActive().map(n -> Map.of("indexed", n));
    }

    /** One liveness batch now (same bounds as the scheduled job). */
    @PostMapping("/liveness")
    public Mono<LivenessReport> checkLiveness() {
        return liveness.check();
    }

    @GetMapping("/inventory")
    public Mono<ListingInventory> inventory() {
        return inventory.inventory();
    }
}
