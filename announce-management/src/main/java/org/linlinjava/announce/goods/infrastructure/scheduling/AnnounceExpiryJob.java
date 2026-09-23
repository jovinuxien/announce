package org.linlinjava.announce.goods.infrastructure.scheduling;

import org.linlinjava.announce.goods.application.announce.AnnouncePublishing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Expires published announces past their date: reads the due ids from the read model and sends one
 * {@code ExpireAnnounce} command each (the aggregate ignores announces that are not due). Disable with
 * {@code announce.expiry.enabled=false}.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "announce.expiry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AnnounceExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(AnnounceExpiryJob.class);
    private static final int BATCH = 200;
    private final AnnouncePublishing publishing;

    public AnnounceExpiryJob(AnnouncePublishing publishing) {
        this.publishing = publishing;
    }

    @Scheduled(cron = "${announce.expiry.cron:0 */15 * * * *}")
    public void expireDueAnnounces() {
        publishing.expireDue(BATCH)
                .subscribe(n -> { if (n > 0) log.info("expired {} announces", n); },
                        e -> log.error("announce expiry failed", e));
    }
}
