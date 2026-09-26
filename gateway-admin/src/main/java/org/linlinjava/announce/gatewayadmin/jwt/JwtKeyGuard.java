package org.linlinjava.announce.gatewayadmin.jwt;

import org.linlinjava.announce.gatewayadmin.config.JwtProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Under the {@code prod} profile the edge refuses to start with the committed development signing key. */
@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class JwtKeyGuard {
    public JwtKeyGuard(JwtProperties properties) {
        if (!properties.hasKeys()) {
            throw new IllegalStateException("FATAL [prod]: announce.jwt.private-key-pem / public-key-pem are blank "
                    + "(ANNOUNCE_ADMIN_JWT_PRIVATE_KEY_PEM / ANNOUNCE_ADMIN_JWT_PUBLIC_KEY_PEM). "
                    + "Refusing to sign admin tokens with the development key pair.");
        }
    }
}
