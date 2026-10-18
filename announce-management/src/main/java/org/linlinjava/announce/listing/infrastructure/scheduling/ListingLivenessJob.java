package org.linlinjava.announce.listing.infrastructure.scheduling;

import org.linlinjava.announce.listing.application.ListingLivenessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Probes a bounded batch of ACTIVE crawled listings (url and image) on {@code announce.listing.liveness.cron}
 * (default every 15 minutes); see {@link ListingLivenessService}. Disable with
 * {@code announce.listing.liveness.enabled=false}.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "announce.listing.liveness", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ListingLivenessJob {

    private static final Logger log = LoggerFactory.getLogger(ListingLivenessJob.class);
    private final ListingLivenessService liveness;

    public ListingLivenessJob(ListingLivenessService liveness) {
        this.liveness = liveness;
    }

    @Scheduled(cron = "${announce.listing.liveness.cron:0 */15 * * * *}")
    public void checkListings() {
        liveness.check().subscribe(r -> { }, e -> log.error("crawled listing liveness check failed", e));
    }
}
