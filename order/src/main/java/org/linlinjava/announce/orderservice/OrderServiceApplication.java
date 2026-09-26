package org.linlinjava.announce.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The order module: four bounded contexts under {@code org.linlinjava.announce.{auction,order,payment,shipping}},
 * each with its own aggregate, projections, jobs, REST and public topic, sharing nothing but the events of
 * {@code order-api}. This package holds only the cross-cutting wiring (clock, persistence, security); no root
 * depends on it and no root depends on another root ({@code ContextBoundariesTest}).
 */
@SpringBootApplication(scanBasePackages = {
        "org.linlinjava.announce.orderservice",
        "org.linlinjava.announce.auction",
        "org.linlinjava.announce.order",
        "org.linlinjava.announce.payment",
        "org.linlinjava.announce.shipping"})
@EnableScheduling
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
