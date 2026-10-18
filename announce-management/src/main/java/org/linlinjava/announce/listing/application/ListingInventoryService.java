package org.linlinjava.announce.listing.application;

import org.linlinjava.announce.listing.domain.CrawledListingRepository;
import org.linlinjava.announce.listing.domain.InventoryCount;
import org.linlinjava.announce.listing.domain.ListingImportRunRepository;
import org.linlinjava.announce.listing.domain.ListingSource;
import org.linlinjava.announce.listing.domain.ListingStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Builds the per-source inventory from the read model only (one grouped count query plus the latest run per source). */
@Service
public class ListingInventoryService {

    static final String UNGRADED = "UNGRADED";

    private final CrawledListingRepository listings;
    private final ListingImportRunRepository runs;

    public ListingInventoryService(CrawledListingRepository listings, ListingImportRunRepository runs) {
        this.listings = listings;
        this.runs = runs;
    }

    public Mono<ListingInventory> inventory() {
        return listings.inventoryCounts().collectList()
                .flatMap(counts -> Flux.fromArray(ListingSource.values())
                        .concatMap(source -> runs.latest(source).map(Optional::of).defaultIfEmpty(Optional.empty())
                                .map(run -> block(source, counts, run.orElse(null))))
                        .collectList())
                .map(ListingInventory::new);
    }

    private static ListingInventory.SourceInventory block(ListingSource source, List<InventoryCount> counts,
                                                          org.linlinjava.announce.listing.domain.ListingImportRun run) {
        long active = 0, gone = 0, missingImage = 0, deadLink = 0, checked = 0;
        Map<String, Long> grades = new LinkedHashMap<>();
        grades.put("GOOD", 0L);
        grades.put("WEAK", 0L);
        grades.put("REJECT", 0L);
        grades.put(UNGRADED, 0L);
        for (InventoryCount c : counts) {
            if (c.source() != source) {
                continue;
            }
            if (c.status() == ListingStatus.ACTIVE) {
                active += c.total();
                missingImage += c.missingImage();
                checked += c.checked();
                grades.merge(c.grade() == null ? UNGRADED : c.grade().name(), c.total(), Long::sum);
            } else {
                gone += c.total();
                deadLink += c.deadLink();
            }
        }
        return new ListingInventory.SourceInventory(source, source.domain(), active, gone, grades, missingImage, deadLink, checked, run);
    }
}
