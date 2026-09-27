package org.linlinjava.announce.order.domain;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * The configurable policies the aggregate applies ({@code doc/order-context.md} §3): the fee schedule fixed at
 * agreement, the pay and ship windows, and the release rule. Built from {@code order.*} by the infrastructure; tests
 * construct it directly.
 *
 * @param releaseOn        DELIVERY (default) or DISPATCH
 * @param deliveryGrace    dispute window after delivery (or after dispatch when {@code releaseOn} is DISPATCH)
 * @param dispatchGrace    release after dispatch when the carrier never reports delivery
 */
public record OrderPolicies(FeeSchedule fees, Duration payWindow, Duration shipWindow, ReleaseOn releaseOn,
                            Duration deliveryGrace, Duration dispatchGrace) {

    public enum ReleaseOn { DELIVERY, DISPATCH }

    public OrderPolicies {
        Objects.requireNonNull(fees, "fees");
        require(payWindow, Duration.ofHours(12), Duration.ofDays(7), "order.window.pay");
        require(shipWindow, Duration.ofDays(3), Duration.ofDays(7), "order.window.ship");
        require(deliveryGrace, Duration.ZERO, Duration.ofDays(14), "order.release.delivery-grace");
        require(dispatchGrace, Duration.ofDays(7), Duration.ofDays(30), "order.release.dispatch-grace");
        releaseOn = releaseOn == null ? ReleaseOn.DELIVERY : releaseOn;
    }

    public static OrderPolicies defaults() {
        return new OrderPolicies(FeeSchedule.of(8, 500, Map.of()), Duration.ofHours(48), Duration.ofDays(7), ReleaseOn.DELIVERY,
                Duration.ofDays(3), Duration.ofDays(14));
    }

    public OrderPolicies withReleaseOn(ReleaseOn on) {
        return new OrderPolicies(fees, payWindow, shipWindow, on, deliveryGrace, dispatchGrace);
    }

    private static void require(Duration value, Duration min, Duration max, String name) {
        Objects.requireNonNull(value, name);
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max + ", was " + value);
        }
    }

    /**
     * Platform fee = percentage of the price plus a fixed amount, per category with a default; never more than the
     * price. Computed once, at agreement.
     */
    public record FeeSchedule(Rate defaultRate, Map<Integer, Rate> byCategory) {

        public record Rate(double percent, long fixedCents) {
            public Rate {
                if (percent < 0 || percent > 100) throw new IllegalArgumentException("fee percent out of range: " + percent);
                if (fixedCents < 0) throw new IllegalArgumentException("fee fixed part must not be negative");
            }
        }

        public FeeSchedule {
            Objects.requireNonNull(defaultRate, "defaultRate");
            byCategory = byCategory == null ? Map.of() : Map.copyOf(byCategory);
        }

        public static FeeSchedule of(double percent, long fixedCents, Map<Integer, Rate> byCategory) {
            return new FeeSchedule(new Rate(percent, fixedCents), byCategory);
        }

        public long feeCents(Integer categoryId, long priceCents) {
            Rate rate = categoryId == null ? defaultRate : byCategory.getOrDefault(categoryId, defaultRate);
            long fee = Math.round(priceCents * rate.percent() / 100.0) + rate.fixedCents();
            return Math.min(Math.max(fee, 0), priceCents);
        }
    }
}
