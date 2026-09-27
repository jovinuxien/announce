package org.linlinjava.announce.order.infrastructure.scheduling;

import org.linlinjava.announce.order.application.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Ship window ({@code doc/order-context.md} §3). A pass over {@code announce_order} that sends commands; the aggregate
 * decides. No scheduler state in the event store.
 */
@Component
@ConditionalOnProperty(prefix = "order.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderShipWindowJob {

    private static final Logger log = LoggerFactory.getLogger(OrderShipWindowJob.class);

    private final OrderService orders;

    public OrderShipWindowJob(OrderService orders) {
        this.orders = orders;
    }

    @Scheduled(cron = "${order.jobs.cron:0 * * * * *}")
    public void run() {
        runOnce().subscribe(n -> { if (n > 0) log.info(" paid orders the seller did not ship within order.window.ship are refunded:Refunded {} unshipped order(s)", n); }, e -> log.error("ShipWindow job failed", e));
    }

    /** One pass, for tests and manual triggers. */
    public Mono<Long> runOnce() {
        return orders.expireShipWindows(200);
    }
}
