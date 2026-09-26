package org.linlinjava.announce.shipping.infrastructure.scheduling;

import org.linlinjava.announce.shipping.application.ShippingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Polls the carrier for every shipment still on its way (carriers that push webhooks need it only as a safety net). */
@Component
@ConditionalOnProperty(prefix = "shipping.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ShipmentTrackingJob {

    private static final Logger log = LoggerFactory.getLogger(ShipmentTrackingJob.class);

    private final ShippingService shipping;

    public ShipmentTrackingJob(ShippingService shipping) {
        this.shipping = shipping;
    }

    @Scheduled(cron = "${shipping.jobs.cron:0 */5 * * * *}")
    public void poll() {
        runOnce().subscribe(n -> { if (n > 0) log.debug("Polled {} shipment(s)", n); }, e -> log.error("Shipment tracking failed", e));
    }

    /** One pass, for tests and manual triggers. */
    public Mono<Long> runOnce() {
        return shipping.pollOpenShipments(200);
    }
}
