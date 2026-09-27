package org.linlinjava.announce.shipping.infrastructure.carrier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** HMAC-SHA256 over the raw webhook body, hex encoded (same scheme as the payment context; each context keeps its own copy). */
public final class CarrierWebhookSignature {
    private CarrierWebhookSignature() {}

    public static String sign(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC not available", e);
        }
    }

    public static boolean verify(String secret, String body, String signature) {
        if (signature == null) return false;
        return MessageDigest.isEqual(sign(secret, body).getBytes(StandardCharsets.UTF_8), signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
