package org.linlinjava.announce.gatewayapi.auth;

import reactor.core.publisher.Mono;

/** Port over {@code announce_refresh_token}; the R2DBC implementation is {@link R2dbcRefreshTokenStore}. */
public interface RefreshTokenStore {

    Mono<Void> save(RefreshTokenRecord record);

    Mono<RefreshTokenRecord> findByHash(String tokenHash);

    Mono<Void> revoke(String id);

    /** Revokes every live token of one member (password change: other devices must log in again). */
    Mono<Void> revokeAllForUser(String userType, int userId);
}
