package org.linlinjava.announce.gatewayadmin.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.linlinjava.announce.gatewayadmin.auth.AdminPrincipal;
import org.linlinjava.announce.gatewayadmin.config.JwtProperties;

import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and verifies the edge's RS256 access tokens. Claims: {@code iss}, {@code aud}, {@code sub} (the account id),
 * {@code jti}, {@code iat}, {@code exp}, {@code typ} ({@code ADMIN}), {@code uid}, {@code username}, {@code roles}
 * (authorities such as {@code ROLE_ADMIN}, {@code ROLE_SUPER}) and {@code roleIds}. Verification checks the
 * signature, issuer, audience and expiry; the caller decides what a non-admin {@code typ} may do.
 */
public class AdminJwtService {

    public static final String TYPE_ADMIN = "ADMIN";

    private final JwtProperties properties;
    private final RSASSASigner signer;
    private final RSASSAVerifier verifier;
    private final Clock clock;

    public AdminJwtService(JwtProperties properties, RsaKeys keys) {
        this(properties, keys, Clock.systemUTC());
    }

    public AdminJwtService(JwtProperties properties, RsaKeys keys, Clock clock) {
        this.properties = properties;
        this.signer = new RSASSASigner(keys.privateKey());
        this.verifier = new RSASSAVerifier(keys.publicKey());
        this.clock = clock;
    }

    public long accessTtlSeconds() {
        return properties.accessTtl().toSeconds();
    }

    public String issue(AdminPrincipal principal) {
        return issue(String.valueOf(principal.id()), TYPE_ADMIN, principal.id(), principal.username(), principal.avatar(),
                List.copyOf(principal.authorities()), principal.roleIds());
    }

    /** Generic issue, also used by tests to mint tokens of another type. */
    public String issue(String subject, String type, Integer uid, String username, String avatar, List<String> roles, List<Integer> roleIds) {
        Instant now = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(properties.issuer())
                .audience(properties.audience())
                .subject(subject)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(properties.accessTtl())))
                .claim("typ", type)
                .claim("uid", uid)
                .claim("username", username)
                .claim("avatar", avatar)
                .claim("roles", roles)
                .claim("roleIds", roleIds)
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(), claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Cannot sign the admin token", e);
        }
        return jwt.serialize();
    }

    /** The verified claims as a principal, or empty when the token is malformed, forged, from another issuer or expired. */
    public Optional<Verified> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(verifier)) {
                return Optional.empty();
            }
            JWTClaimsSet c = jwt.getJWTClaimsSet();
            if (!properties.issuer().equals(c.getIssuer()) || c.getAudience() == null || !c.getAudience().contains(properties.audience())) {
                return Optional.empty();
            }
            if (c.getExpirationTime() == null || !c.getExpirationTime().toInstant().isAfter(clock.instant())) {
                return Optional.empty();
            }
            List<String> roles = c.getStringListClaim("roles");
            List<?> rawRoleIds = c.getListClaim("roleIds");
            List<Integer> roleIds = rawRoleIds == null ? List.of() : rawRoleIds.stream().map(o -> ((Number) o).intValue()).toList();
            Integer uid = c.getIntegerClaim("uid");
            String type = c.getStringClaim("typ");
            AdminPrincipal principal = new AdminPrincipal(uid != null ? uid : Integer.valueOf(c.getSubject()), c.getStringClaim("username"),
                    c.getStringClaim("avatar"), roleIds, roles == null ? new LinkedHashSet<>() : new LinkedHashSet<>(roles));
            return Optional.of(new Verified(type == null ? "" : type, principal, c.getJWTID()));
        } catch (ParseException | JOSEException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    public record Verified(String type, AdminPrincipal principal, String tokenId) {
        public boolean isAdmin() {
            return TYPE_ADMIN.equals(type);
        }
    }
}
