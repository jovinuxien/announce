package org.linlinjava.announce.cqrs.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

/**
 * The machine credential every edge (and every service calling another service) puts in {@code X-Service-Token}:
 * <pre>v1.&lt;service&gt;.&lt;epochSeconds&gt;.&lt;hex hmacSha256(secret, service + ":" + epochSeconds)&gt;</pre>
 * It proves that the request passed a holder of the shared secret, so a backend may trust the identity headers
 * ({@code X-User-Id}, {@code X-User-Type}, ...) that come with it. It is not bound to the request: it is only ever
 * sent between our own processes and expires with the allowed clock skew.
 *
 * <p>The edges do not depend on this module and carry their own copy of {@link #mint}; a shared test vector
 * (secret {@code test-secret}, service {@code gateway-api}, epoch {@code 1767225600}) pins the format on both sides.
 */
public final class ServiceTokens {

    public static final String HEADER = "X-Service-Token";
    public static final String VERSION = "v1";

    private ServiceTokens() {
    }

    public static String mint(String service, String secret, long epochSeconds) {
        if (service == null || service.isBlank() || service.indexOf('.') >= 0) {
            throw new IllegalArgumentException("service name must be non-blank and must not contain a dot: " + service);
        }
        return VERSION + "." + service + "." + epochSeconds + "." + hmac(secret, service + ":" + epochSeconds);
    }

    /**
     * @return the name of the service that minted the token, or empty when the token is missing, malformed, signed
     * with another secret, or older (or newer) than {@code maxSkewSeconds}
     */
    public static Optional<String> verify(String secret, String token, long nowEpochSeconds, long maxSkewSeconds) {
        if (token == null || secret == null || secret.isEmpty()) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 4 || !VERSION.equals(parts[0]) || parts[1].isEmpty()) {
            return Optional.empty();
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (Math.abs(nowEpochSeconds - timestamp) > maxSkewSeconds) {
            return Optional.empty();
        }
        byte[] expected = hmac(secret, parts[1] + ":" + timestamp).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = parts[3].getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual) ? Optional.of(parts[1]) : Optional.empty();
    }

    private static String hmac(String secret, String payload) {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalStateException("the service-token secret is not configured");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
