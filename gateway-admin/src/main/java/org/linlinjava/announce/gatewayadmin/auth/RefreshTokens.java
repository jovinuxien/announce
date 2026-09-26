package org.linlinjava.announce.gatewayadmin.auth;

import org.linlinjava.announce.db.dao.reactive.AnnounceRefreshTokenMapper;
import org.linlinjava.announce.db.domain.AnnounceRefreshToken;
import org.linlinjava.announce.db.domain.AnnounceRefreshTokenExample;
import org.linlinjava.announce.gatewayadmin.config.RefreshProperties;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Opaque refresh tokens for staff, stored hashed in {@code announce_refresh_token} with {@code user_type = ADMIN}.
 * A token is single use: {@link #rotate} revokes it and issues the next one; {@link #revoke} is the logout path.
 * Only the SHA-256 of the token is stored, so a database leak does not yield usable tokens.
 */
@Service
public class RefreshTokens {

    public static final String USER_TYPE = "ADMIN";

    private final AnnounceRefreshTokenMapper tokens;
    private final RefreshProperties properties;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokens(AnnounceRefreshTokenMapper tokens, RefreshProperties properties) {
        this.tokens = tokens;
        this.properties = properties;
    }

    public record Rotation(Integer adminId, String nextToken) {}

    /** Issues a new token for the admin and returns the raw value (the only time it exists in clear). */
    public Mono<String> issue(Integer adminId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime now = LocalDateTime.now();
        AnnounceRefreshToken row = new AnnounceRefreshToken();
        row.setId(UUID.randomUUID().toString());
        row.setUserType(USER_TYPE);
        row.setUserId(adminId);
        row.setTokenHash(hash(raw));
        row.setExpiresAt(now.plus(properties.ttl()));
        row.setRevoked(false);
        row.setAddTime(now);
        row.setUpdateTime(now);
        return tokens.insertSelective(row).thenReturn(raw);
    }

    /** Validates and revokes the given token, then issues the next one for the same admin. */
    public Mono<Rotation> rotate(String raw) {
        if (raw == null || raw.isBlank()) {
            return Mono.error(new InvalidRefreshTokenException("missing refresh token"));
        }
        return find(raw)
                .filter(row -> !Boolean.TRUE.equals(row.getRevoked()) && row.getExpiresAt() != null && row.getExpiresAt().isAfter(LocalDateTime.now()))
                .switchIfEmpty(Mono.error(new InvalidRefreshTokenException("invalid or expired refresh token")))
                .flatMap(row -> revokeRow(row).then(issue(row.getUserId())).map(next -> new Rotation(row.getUserId(), next)));
    }

    /** Logout: revokes the token when it exists; an unknown token is not an error. */
    public Mono<Void> revoke(String raw) {
        if (raw == null || raw.isBlank()) {
            return Mono.empty();
        }
        return find(raw).flatMap(this::revokeRow).then();
    }

    private Mono<AnnounceRefreshToken> find(String raw) {
        AnnounceRefreshTokenExample example = new AnnounceRefreshTokenExample();
        example.or().andTokenHashEqualTo(hash(raw)).andUserTypeEqualTo(USER_TYPE);
        return tokens.selectOneByExample(example);
    }

    private Mono<Integer> revokeRow(AnnounceRefreshToken row) {
        AnnounceRefreshToken patch = new AnnounceRefreshToken();
        patch.setId(row.getId());
        patch.setRevoked(true);
        patch.setUpdateTime(LocalDateTime.now());
        return tokens.updateByPrimaryKeySelective(patch);
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
