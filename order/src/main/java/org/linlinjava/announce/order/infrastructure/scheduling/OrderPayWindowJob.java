package org.linlinjava.announce.order.infrastructure.scheduling;

import org.linlinjava.announce.order.application.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Pay window ({@code doc/order-context.md} §3). A pass over {@code announce_order} that sends commands; the aggregate
 * decides. No scheduler state in the event store.
 */
@Component
@ConditionalOnProperty(prefix = "order.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderPayWindowJob {

    private static final Logger log = LoggerFactory.getLogger(OrderPayWindowJob.class);

    private final OrderService orders;

    public OrderPayWindowJob(OrderService orders) {
        this.orders = orders;
    }

    @Scheduled(cron = "${order.jobs.cron:0 * * * * *}")
    public void run() {
        runOnce().subscribe(n -> { if (n > 0) log.info(" agreed but unpaid orders past order.window.pay are cancelled (announce stays PUBLISHED):Cancelled {} unpaid order(s)", n); }, e -> log.error("PayWindow job failed", e));
    }

    /** One pass, for tests and manual triggers. */
    public Mono<Long> runOnce() {
        return orders.expirePayWindows(200);
    }
}
