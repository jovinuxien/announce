package org.linlinjava.announce.gatewayapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code announce.jwt.*}: the RS256 keypair and claims of the member access tokens this edge issues and verifies.
 * Blank PEMs mean an ephemeral keypair (development only; tokens die with the process).
 */
@ConfigurationProperties(prefix = "announce.jwt")
public record JwtProperties(String issuer, String audience, Duration accessTtl, String privateKeyPem, String publicKeyPem) {

    public JwtProperties {
        if (issuer == null || issuer.isBlank()) issuer = "announce-customer";
        if (audience == null || audience.isBlank()) audience = "announce-customer-api";
        if (accessTtl == null) accessTtl = Duration.ofHours(2);
    }
}
