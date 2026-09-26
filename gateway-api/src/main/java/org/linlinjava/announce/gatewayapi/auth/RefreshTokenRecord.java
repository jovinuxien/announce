package org.linlinjava.announce.gatewayapi.auth;

import java.time.LocalDateTime;

/** One row of {@code announce_refresh_token}: only the SHA-256 hash of the opaque token is kept. */
public record RefreshTokenRecord(String id, String userType, int userId, String tokenHash, LocalDateTime expiresAt, boolean revoked) {
}
