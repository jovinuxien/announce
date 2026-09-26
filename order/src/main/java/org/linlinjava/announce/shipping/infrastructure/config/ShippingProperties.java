package org.linlinjava.announce.shipping.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code shipping.*}: which carrier adapter is active, the fake carrier's webhook secret and the tracking job. */
@ConfigurationProperties(prefix = "shipping")
public class ShippingProperties {

    /** {@code fake} (default) or {@code postnord} (stub). */
    private String carrier = "fake";
    private final Fake fake = new Fake();
    private final Jobs jobs = new Jobs();

    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }
    public Fake getFake() { return fake; }
    public Jobs getJobs() { return jobs; }

    public static class Fake {
        private String secret = "fake-carrier-secret";
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
    }

    public static class Jobs {
        private boolean enabled = true;
        private String cron = "0 */5 * * * *";
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }
    }
}
