package org.linlinjava.announce.gatewayadmin.jwt;

import org.linlinjava.announce.gatewayadmin.config.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Bean
    public RsaKeys adminJwtKeys(JwtProperties properties) {
        if (properties.hasKeys()) {
            return RsaKeys.from(properties.privateKeyPem(), properties.publicKeyPem());
        }
        log.warn("announce.jwt.*-key-pem not set: signing admin tokens with the COMMITTED development key pair. Never do this in production.");
        return RsaKeys.developmentPair();
    }

    @Bean
    public AdminJwtService adminJwtService(JwtProperties properties, RsaKeys keys) {
        return new AdminJwtService(properties, keys);
    }
}
