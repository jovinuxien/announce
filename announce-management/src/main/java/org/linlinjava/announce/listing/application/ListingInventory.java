package org.linlinjava.announce.listing.application;

import org.linlinjava.announce.listing.domain.ListingImportRun;
import org.linlinjava.announce.listing.domain.ListingSource;

import java.util.List;
import java.util.Map;

/**
 * The answer of {@code GET /api/admin/listings/inventory}: one block per source. {@code grades} counts the ACTIVE rows
 * per grade ({@code UNGRADED} for rows imported before grading existed), {@code missingImage} the ACTIVE rows without
 * an image url or with a dead image, {@code deadLink} the rows the liveness check set GONE, {@code checked} the ACTIVE
 * rows probed at least once, {@code lastImport} the latest recorded run of the source (null when none).
 */
public record ListingInventory(List<SourceInventory> sources) {

    public record SourceInventory(ListingSource source, String domain, long active, long gone, Map<String, Long> grades,
                                  long missingImage, long deadLink, long checked, ListingImportRun lastImport) {
    }
}
