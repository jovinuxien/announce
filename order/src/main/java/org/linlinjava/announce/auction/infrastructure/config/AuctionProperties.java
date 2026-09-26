package org.linlinjava.announce.auction.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** {@code auction.*}: what an auction opened from an announce gets when the announce carries no auction settings of its own. */
@ConfigurationProperties(prefix = "auction")
public class AuctionProperties {

    private final SellerAccount sellerAccount = new SellerAccount();
    private final Jobs jobs = new Jobs();
    /** Bid increment in minor units. */
    private long defaultIncrementCents = 1000;
    /** Anti-sniping window in seconds; 0 switches it off. */
    private int defaultAutoExtendSec = 120;
    /** How long an auction runs from publication, capped by the announce's expiry. */
    private Duration defaultDuration = Duration.ofDays(7);

    public SellerAccount getSellerAccount() { return sellerAccount; }
    public Jobs getJobs() { return jobs; }
    public long getDefaultIncrementCents() { return defaultIncrementCents; }
    public void setDefaultIncrementCents(long v) { this.defaultIncrementCents = v; }
    public int getDefaultAutoExtendSec() { return defaultAutoExtendSec; }
    public void setDefaultAutoExtendSec(int v) { this.defaultAutoExtendSec = v; }
    public Duration getDefaultDuration() { return defaultDuration; }
    public void setDefaultDuration(Duration v) { this.defaultDuration = v; }

    public static class SellerAccount {
        /** When true, OpenAuction is refused until the seller's payout account is enabled (payment.v1). */
        private boolean required = true;
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
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
