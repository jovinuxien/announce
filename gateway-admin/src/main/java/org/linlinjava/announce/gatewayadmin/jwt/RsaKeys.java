package org.linlinjava.announce.gatewayadmin.jwt;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * The RS256 key pair: a PKCS#8 private key and an X.509 public key in PEM. {@link #development()} loads the pair
 * committed under {@code config/} for local runs; production must provide its own through the environment.
 */
public record RsaKeys(RSAPrivateKey privateKey, RSAPublicKey publicKey, boolean isDevelopment) {

    public static RsaKeys from(String privatePem, String publicPem) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPrivateKey priv = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(decode(privatePem, "PRIVATE KEY")));
            RSAPublicKey pub = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(decode(publicPem, "PUBLIC KEY")));
            return new RsaKeys(priv, pub, false);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Invalid RSA key material for the admin JWT", e);
        }
    }

    public static RsaKeys developmentPair() {
        RsaKeys keys = from(read("config/dev-admin-jwt-private.pem"), read("config/dev-admin-jwt-public.pem"));
        return new RsaKeys(keys.privateKey(), keys.publicKey(), true);
    }

    private static String read(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Development key " + path + " is missing from the classpath", e);
        }
    }

    private static byte[] decode(String pem, String label) {
        String body = pem.replace("-----BEGIN " + label + "-----", "").replace("-----END " + label + "-----", "")
                .replaceAll("\\s", "");
        if (body.isEmpty()) {
            throw new IllegalStateException("Empty PEM for " + label);
        }
        return Base64.getDecoder().decode(body);
    }
}
