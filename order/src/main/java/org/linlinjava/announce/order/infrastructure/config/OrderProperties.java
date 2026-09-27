package org.linlinjava.announce.order.infrastructure.config;

import org.linlinjava.announce.order.domain.OrderPolicies;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** {@code order.*}: the deal's currency, windows, release rule, fee schedule and seller gate ({@code doc/order-context.md} §3). */
@ConfigurationProperties(prefix = "order")
public class OrderProperties {

    private String currency = "DKK";
    private final Window window = new Window();
    private final Release release = new Release();
    private final SellerAccount sellerAccount = new SellerAccount();
    private final Fee fee = new Fee();
    private final Jobs jobs = new Jobs();

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Window getWindow() { return window; }
    public Release getRelease() { return release; }
    public SellerAccount getSellerAccount() { return sellerAccount; }
    public Fee getFee() { return fee; }
    public Jobs getJobs() { return jobs; }

    /** The validated domain policy built from these settings. */
    public OrderPolicies toPolicies() {
        Map<Integer, OrderPolicies.FeeSchedule.Rate> byCategory = new LinkedHashMap<>();
        fee.getCategories().forEach((id, rate) -> byCategory.put(id, new OrderPolicies.FeeSchedule.Rate(rate.getPercent(), rate.getFixedCents())));
        OrderPolicies.FeeSchedule fees = new OrderPolicies.FeeSchedule(
                new OrderPolicies.FeeSchedule.Rate(fee.getDefault().getPercent(), fee.getDefault().getFixedCents()), byCategory);
        return new OrderPolicies(fees, window.getPay(), window.getShip(), release.getOn(), release.getDeliveryGrace(), release.getDispatchGrace());
    }

    public static class Window {
        private Duration pay = Duration.ofHours(48);
        private Duration ship = Duration.ofDays(7);
        public Duration getPay() { return pay; }
        public void setPay(Duration pay) { this.pay = pay; }
        public Duration getShip() { return ship; }
        public void setShip(Duration ship) { this.ship = ship; }
    }

    public static class Release {
        private OrderPolicies.ReleaseOn on = OrderPolicies.ReleaseOn.DELIVERY;
        private Duration deliveryGrace = Duration.ofDays(3);
        private Duration dispatchGrace = Duration.ofDays(14);
        public OrderPolicies.ReleaseOn getOn() { return on; }
        public void setOn(OrderPolicies.ReleaseOn on) { this.on = on; }
        public Duration getDeliveryGrace() { return deliveryGrace; }
        public void setDeliveryGrace(Duration v) { this.deliveryGrace = v; }
        public Duration getDispatchGrace() { return dispatchGrace; }
        public void setDispatchGrace(Duration v) { this.dispatchGrace = v; }
    }

    public static class SellerAccount {
        private boolean required = true;
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
    }

    public static class Rate {
        private double percent;
        private long fixedCents;
        public double getPercent() { return percent; }
        public void setPercent(double percent) { this.percent = percent; }
        public long getFixedCents() { return fixedCents; }
        public void setFixedCents(long fixedCents) { this.fixedCents = fixedCents; }
    }

    public static class Fee {
        private final Rate defaultRate = new Rate();
        private Map<Integer, Rate> categories = new LinkedHashMap<>();
        public Fee() { defaultRate.setPercent(8); defaultRate.setFixedCents(500); }
        public Rate getDefault() { return defaultRate; }
        public Map<Integer, Rate> getCategories() { return categories; }
        public void setCategories(Map<Integer, Rate> categories) { this.categories = categories; }
    }

    public static class Jobs {
        private boolean enabled = true;
        private String cron = "0 * * * * *";
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }
    }
}
