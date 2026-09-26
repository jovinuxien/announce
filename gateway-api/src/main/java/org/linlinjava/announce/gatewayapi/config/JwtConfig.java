package org.linlinjava.announce.gatewayapi.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.util.List;

/** RS256 encoder and decoder over the keypair of {@link JwtProperties}; one BCrypt encoder for the member passwords. */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {

    @Bean
    RsaKeys rsaKeys(JwtProperties props) {
        return RsaKeys.from(props);
    }

    @Bean
    JwtEncoder jwtEncoder(RsaKeys keys) {
        RSAKey jwk = new RSAKey.Builder(keys.publicKey()).privateKey(keys.privateKey()).keyID("announce-customer").build();
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(new JWKSet(jwk));
        return new NimbusJwtEncoder(source);
    }

    /** Local public key, so decoding is pure CPU work and safe to call on an event-loop thread. */
    @Bean
    JwtDecoder jwtDecoder(RsaKeys keys, JwtProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey()).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>("aud",
                aud -> aud != null && aud.contains(props.audience()));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(props.issuer()), audience));
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
