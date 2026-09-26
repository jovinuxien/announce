package org.linlinjava.announce.gatewayapi.machine;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code announce.machine.*}: the service name and the shared secret of the {@code X-Service-Token} HMAC. */
@ConfigurationProperties(prefix = "announce.machine")
public record MachineTokenProperties(String service, String secret) {

    public MachineTokenProperties {
        if (service == null || service.isBlank()) service = "gateway-api";
    }
}
