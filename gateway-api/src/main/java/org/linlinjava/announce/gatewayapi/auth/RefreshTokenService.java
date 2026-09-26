package org.linlinjava.announce.gatewayapi.auth;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Opaque, single-use refresh tokens: {@code <id>.<secret>} handed to the client, only SHA-256(secret) stored.
 * {@link #rotate} revokes the presented token and issues a new one; a revoked, expired or unknown token is rejected.
 */
@Service
public class RefreshTokenService {

    public static final String USER_TYPE_MEMBER = "MEMBER";

    private final RefreshTokenStore store;
    private final AuthProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenStore store, AuthProperties props, Clock clock) {
        this.store = store;
        this.props = props;
        this.clock = clock;
    }

    /** The opaque token and the member it belongs to. */
    public record Issued(String token, int userId) {
    }

    public Mono<Issued> issue(int userId) {
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String id = UUID.randomUUID().toString();
        String token = id + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        RefreshTokenRecord record = new RefreshTokenRecord(id, USER_TYPE_MEMBER, userId, hash(token),
                LocalDateTime.now(clock).plus(props.refreshTtl()), false);
        return store.save(record).thenReturn(new Issued(token, userId));
    }

    public Mono<Issued> rotate(String presented) {
        return valid(presented).flatMap(r -> store.revoke(r.id()).then(issue(r.userId())));
    }

    /** Revokes the token if it is known; unknown tokens are ignored so logout never fails. */
    public Mono<Void> revoke(String presented) {
        if (presented == null || presented.isBlank()) return Mono.empty();
        return store.findByHash(hash(presented)).flatMap(r -> store.revoke(r.id()));
    }

    private Mono<RefreshTokenRecord> valid(String presented) {
        if (presented == null || presented.isBlank()) return Mono.error(AuthException.unauthorized("missing refresh token"));
        return store.findByHash(hash(presented))
                .switchIfEmpty(Mono.error(AuthException.unauthorized("unknown refresh token")))
                .flatMap(r -> {
                    if (r.revoked()) return Mono.error(AuthException.unauthorized("refresh token revoked"));
                    if (!USER_TYPE_MEMBER.equals(r.userType())) return Mono.error(AuthException.unauthorized("not a member token"));
                    if (r.expiresAt().isBefore(LocalDateTime.now(clock))) return Mono.error(AuthException.unauthorized("refresh token expired"));
                    return Mono.just(r);
                });
    }

    static String hash(String token) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
