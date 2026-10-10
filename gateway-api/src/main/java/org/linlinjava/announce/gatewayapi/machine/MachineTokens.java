package org.linlinjava.announce.gatewayapi.machine;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * {@code X-Service-Token = v1.<service>.<epochSeconds>.<hex hmacSha256(service:epochSeconds)>}. The backends
 * verify it with the same secret ({@code ANNOUNCE_SERVICE_SECRET}) and reject stale timestamps: see
 * {@code org.linlinjava.announce.cqrs.security.ServiceTokens} in cqrs-reactive, which this class must stay
 * compatible with (the edges do not depend on that module; {@code MachineTokensTest} pins the shared vector).
 */
public final class MachineTokens {

    public static final String HEADER = "X-Service-Token";
    public static final String VERSION = "v1";

    private MachineTokens() {
    }

    public static String mint(MachineTokenProperties props, long epochSeconds) {
        String payload = props.service() + ":" + epochSeconds;
        return VERSION + "." + props.service() + "." + epochSeconds + "." + hmac(props.secret(), payload);
    }

    public static boolean verify(MachineTokenProperties props, String token, long nowEpochSeconds, long maxSkewSeconds) {
        if (token == null) return false;
        String[] parts = token.split("\\.");
        if (parts.length != 4 || !VERSION.equals(parts[0]) || !props.service().equals(parts[1])) return false;
        long ts;
        try {
            ts = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(nowEpochSeconds - ts) > maxSkewSeconds) return false;
        return MessageDigest.isEqual(hmac(props.secret(), parts[1] + ":" + ts).getBytes(StandardCharsets.US_ASCII),
                parts[3].getBytes(StandardCharsets.US_ASCII));
    }

    private static String hmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
