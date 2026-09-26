package org.linlinjava.announce.payment.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code payment.*}: which provider adapter is active and the fake provider's webhook secret. */
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {

    /** {@code fake} (default) or {@code stripe} (stub). */
    private String provider = "fake";
    private final Fake fake = new Fake();

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public Fake getFake() { return fake; }

    public static class Fake {
        /** HMAC-SHA256 key the fake provider signs its webhooks with. */
        private String secret = "fake-webhook-secret";
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
    }
}
