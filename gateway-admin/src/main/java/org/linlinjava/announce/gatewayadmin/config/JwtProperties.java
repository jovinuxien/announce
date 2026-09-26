package org.linlinjava.announce.gatewayadmin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** {@code announce.jwt.*}: RS256 access tokens issued by this edge. Blank PEMs select the committed development pair. */
@ConfigurationProperties(prefix = "announce.jwt")
public record JwtProperties(String issuer, String audience, Duration accessTtl, String privateKeyPem, String publicKeyPem) {
    public JwtProperties {
        issuer = issuer == null || issuer.isBlank() ? "announce-admin" : issuer;
        audience = audience == null || audience.isBlank() ? "announce-admin" : audience;
        accessTtl = accessTtl == null ? Duration.ofHours(2) : accessTtl;
        privateKeyPem = privateKeyPem == null ? "" : privateKeyPem;
        publicKeyPem = publicKeyPem == null ? "" : publicKeyPem;
    }

    public boolean hasKeys() {
        return !privateKeyPem.isBlank() && !publicKeyPem.isBlank();
    }
}
