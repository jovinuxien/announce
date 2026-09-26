package org.linlinjava.announce.listing.infrastructure.scheduling;

import org.linlinjava.announce.listing.application.ListingSweepService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sets crawled listings the feeds have not shown for the grace period GONE and drops them from the index
 * (see {@link ListingSweepService}). Disable with {@code announce.listing.sweep.enabled=false}.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "announce.listing.sweep", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ListingSweepJob {

    private static final Logger log = LoggerFactory.getLogger(ListingSweepJob.class);
    private static final int BATCH = 500;
    private final ListingSweepService sweep;

    public ListingSweepJob(ListingSweepService sweep) {
        this.sweep = sweep;
    }

    @Scheduled(cron = "${announce.listing.sweep.cron:0 30 * * * *}")
    public void sweepStaleListings() {
        sweep.sweep(BATCH).subscribe(n -> { }, e -> log.error("crawled listing sweep failed", e));
    }
}
