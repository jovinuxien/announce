package org.linlinjava.announce.gatewayapi.machine;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * {@code X-Service-Token = v1.<service>.<epochSeconds>.<hex hmacSha256(service:epochSeconds)>}. Backends that
 * verify it recompute the HMAC with the same secret and reject stale timestamps; today they only trust the headers.
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
        return hmac(props.secret(), parts[1] + ":" + ts).equals(parts[3]);
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
