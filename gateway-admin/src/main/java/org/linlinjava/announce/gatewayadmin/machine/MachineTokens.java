package org.linlinjava.announce.gatewayadmin.machine;

import org.linlinjava.announce.gatewayadmin.config.MachineTokenProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.HexFormat;

/**
 * {@code X-Service-Token = <issuer>.<epoch-seconds>.<hmac-sha256(secret, issuer + "." + epoch-seconds)>}: proves
 * to a backend that the request passed an edge. Backends verify it (and its age) in a later step.
 */
@Component
public class MachineTokens {

    private final MachineTokenProperties properties;
    private final Clock clock;

    @Autowired
    public MachineTokens(MachineTokenProperties properties) {
        this(properties, Clock.systemUTC());
    }

    MachineTokens(MachineTokenProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public String token() {
        long ts = clock.instant().getEpochSecond();
        return properties.issuer() + "." + ts + "." + sign(properties.issuer() + "." + ts);
    }

    /** Verification for a backend (or a test): issuer, timestamp within {@code maxAgeSeconds} and a matching signature. */
    public boolean verify(String token, long maxAgeSeconds) {
        if (token == null) {
            return false;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return false;
        }
        try {
            long ts = Long.parseLong(parts[1]);
            long age = Math.abs(clock.instant().getEpochSecond() - ts);
            return age <= maxAgeSeconds && sign(parts[0] + "." + parts[1]).equals(parts[2]);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
