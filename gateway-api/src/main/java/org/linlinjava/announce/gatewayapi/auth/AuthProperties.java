package org.linlinjava.announce.gatewayapi.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** {@code announce.auth.*}: the refresh cookie and the refresh-token lifetime. */
@ConfigurationProperties(prefix = "announce.auth")
public record AuthProperties(String cookieName, boolean cookieSecure, Duration refreshTtl) {

    public AuthProperties {
        if (cookieName == null || cookieName.isBlank()) cookieName = "announce_refresh";
        if (refreshTtl == null) refreshTtl = Duration.ofDays(30);
    }
}
