package org.linlinjava.announce.gatewayapi.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Parses the PKCS#8 private and X.509 public PEM of {@link JwtProperties}, or mints an ephemeral pair when blank. */
public record RsaKeys(RSAPublicKey publicKey, RSAPrivateKey privateKey) {

    private static final Logger LOG = LoggerFactory.getLogger(RsaKeys.class);

    public static RsaKeys from(JwtProperties props) {
        boolean blank = isBlank(props.privateKeyPem()) || isBlank(props.publicKeyPem());
        if (blank) {
            LOG.warn("announce.jwt.private-key-pem / public-key-pem are blank: minting an EPHEMERAL RSA keypair; "
                    + "every access token becomes invalid when this process stops");
            return ephemeral();
        }
        try {
            KeyFactory rsa = KeyFactory.getInstance("RSA");
            RSAPrivateKey priv = (RSAPrivateKey) rsa.generatePrivate(new PKCS8EncodedKeySpec(der(props.privateKeyPem(), "PRIVATE KEY")));
            RSAPublicKey pub = (RSAPublicKey) rsa.generatePublic(new X509EncodedKeySpec(der(props.publicKeyPem(), "PUBLIC KEY")));
            return new RsaKeys(pub, priv);
        } catch (Exception e) {
            throw new IllegalStateException("announce.jwt keys are not a PKCS#8 private / X.509 public RSA PEM pair", e);
        }
    }

    static RsaKeys ephemeral() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair pair = gen.generateKeyPair();
            return new RsaKeys((RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] der(String pem, String label) {
        String body = pem.replace("-----BEGIN " + label + "-----", "").replace("-----END " + label + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
