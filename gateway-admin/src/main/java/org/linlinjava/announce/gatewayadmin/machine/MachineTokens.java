package org.linlinjava.announce.gatewayadmin.machine;

import org.linlinjava.announce.gatewayadmin.config.MachineTokenProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;

/**
 * {@code X-Service-Token = v1.<issuer>.<epochSeconds>.<hex hmacSha256(secret, issuer + ":" + epochSeconds)>}: proves
 * to a backend that the request passed an edge. The backends verify it (signature and age) with
 * {@code org.linlinjava.announce.cqrs.security.ServiceTokens} in cqrs-reactive; this edge does not depend on that
 * module, so the layout is kept identical here and in gateway-api, and {@code MachineTokensTest} pins the shared
 * vector.
 */
@Component
public class MachineTokens {

    public static final String VERSION = "v1";

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
        return VERSION + "." + properties.issuer() + "." + ts + "." + sign(properties.issuer() + ":" + ts);
    }

    /** Verification for a backend (or a test): issuer, timestamp within {@code maxAgeSeconds} and a matching signature. */
    public boolean verify(String token, long maxAgeSeconds) {
        if (token == null) {
            return false;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 4 || !VERSION.equals(parts[0]) || !properties.issuer().equals(parts[1])) {
            return false;
        }
        try {
            long ts = Long.parseLong(parts[2]);
            long age = Math.abs(clock.instant().getEpochSecond() - ts);
            return age <= maxAgeSeconds && MessageDigest.isEqual(
                    sign(parts[1] + ":" + ts).getBytes(StandardCharsets.US_ASCII), parts[3].getBytes(StandardCharsets.US_ASCII));
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
