package org.linlinjava.announce.auction.infrastructure.scheduling;

import org.linlinjava.announce.auction.application.AuctionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Timer over the read model ({@code announce_auction}: OPEN and {@code end_time <= now}) that sends one
 * {@code CloseAuction} per due auction; the aggregate decides won or unsold. No scheduler state in the event store.
 */
@Component
@ConditionalOnProperty(prefix = "auction.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuctionClosingJob {

    private static final Logger log = LoggerFactory.getLogger(AuctionClosingJob.class);

    private final AuctionService auctions;

    public AuctionClosingJob(AuctionService auctions) {
        this.auctions = auctions;
    }

    @Scheduled(cron = "${auction.jobs.cron:0 * * * * *}")
    public void closeDueAuctions() {
        runOnce().subscribe(n -> { if (n > 0) log.info("Closed {} auction(s)", n); }, e -> log.error("Auction closing failed", e));
    }

    /** One pass, for tests and manual triggers. */
    public Mono<Long> runOnce() {
        return auctions.closeDue(200);
    }
}
