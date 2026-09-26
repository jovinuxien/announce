package org.linlinjava.announce.gatewayadmin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code announce.machine.*}: the shared secret behind {@code X-Service-Token}. */
@ConfigurationProperties(prefix = "announce.machine")
public record MachineTokenProperties(String secret, String issuer) {
    public MachineTokenProperties {
        issuer = issuer == null || issuer.isBlank() ? "gateway-admin" : issuer;
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("announce.machine.secret must be set");
        }
    }
}
