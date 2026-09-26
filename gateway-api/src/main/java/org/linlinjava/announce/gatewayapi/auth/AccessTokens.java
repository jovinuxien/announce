package org.linlinjava.announce.gatewayapi.auth;

import org.linlinjava.announce.gatewayapi.config.JwtProperties;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Issues the member access token: RS256, {@code sub} = member id, {@code typ} = MEMBER, {@code username}. */
@Component
public class AccessTokens {

    public static final String CLAIM_TYPE = "typ";
    public static final String CLAIM_USERNAME = "username";
    public static final String TYPE_MEMBER = "MEMBER";

    private final JwtEncoder encoder;
    private final JwtProperties props;
    private final Clock clock;

    public AccessTokens(JwtEncoder encoder, JwtProperties props, Clock clock) {
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    public record Issued(String token, long expiresInSeconds) {
    }

    public Issued issue(Account account) {
        Instant now = Instant.now(clock);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .audience(List.of(props.audience()))
                .subject(String.valueOf(account.id()))
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTtl()))
                .claim(CLAIM_TYPE, TYPE_MEMBER)
                .claim(CLAIM_USERNAME, account.username())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return new Issued(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), props.accessTtl().toSeconds());
    }
}
