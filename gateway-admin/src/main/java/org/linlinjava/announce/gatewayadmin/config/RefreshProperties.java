package org.linlinjava.announce.gatewayadmin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** {@code announce.refresh.*}: the httpOnly refresh cookie scoped to {@code /auth}. */
@ConfigurationProperties(prefix = "announce.refresh")
public record RefreshProperties(Duration ttl, String cookieName, boolean cookieSecure) {
    public RefreshProperties {
        ttl = ttl == null ? Duration.ofDays(30) : ttl;
        cookieName = cookieName == null || cookieName.isBlank() ? "announce_admin_refresh" : cookieName;
    }
}
