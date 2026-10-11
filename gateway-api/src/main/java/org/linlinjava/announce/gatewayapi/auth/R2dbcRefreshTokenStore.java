package org.linlinjava.announce.gatewayapi.auth;

import org.linlinjava.announce.db.dao.reactive.AnnounceRefreshTokenMapper;
import org.linlinjava.announce.db.domain.AnnounceRefreshToken;
import org.linlinjava.announce.db.domain.AnnounceRefreshTokenExample;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/** {@link RefreshTokenStore} over the reactive {@code announce_refresh_token} mapper of db-reactive. */
@Component
public class R2dbcRefreshTokenStore implements RefreshTokenStore {

    private final AnnounceRefreshTokenMapper tokens;

    public R2dbcRefreshTokenStore(AnnounceRefreshTokenMapper tokens) {
        this.tokens = tokens;
    }

    @Override
    public Mono<Void> save(RefreshTokenRecord r) {
        LocalDateTime now = LocalDateTime.now();
        AnnounceRefreshToken row = new AnnounceRefreshToken();
        row.setId(r.id());
        row.setUserType(r.userType());
        row.setUserId(r.userId());
        row.setTokenHash(r.tokenHash());
        row.setExpiresAt(r.expiresAt());
        row.setRevoked(r.revoked());
        row.setAddTime(now);
        row.setUpdateTime(now);
        return tokens.insert(row).then();
    }

    @Override
    public Mono<RefreshTokenRecord> findByHash(String tokenHash) {
        AnnounceRefreshTokenExample e = new AnnounceRefreshTokenExample();
        e.or().andTokenHashEqualTo(tokenHash);
        return tokens.selectOneByExample(e).map(row -> new RefreshTokenRecord(row.getId(), row.getUserType(), row.getUserId(),
                row.getTokenHash(), row.getExpiresAt(), Boolean.TRUE.equals(row.getRevoked())));
    }

    @Override
    public Mono<Void> revokeAllForUser(String userType, int userId) {
        AnnounceRefreshTokenExample e = new AnnounceRefreshTokenExample();
        e.or().andUserTypeEqualTo(userType).andUserIdEqualTo(userId).andRevokedEqualTo(false);
        AnnounceRefreshToken row = new AnnounceRefreshToken();
        row.setRevoked(true);
        row.setUpdateTime(LocalDateTime.now());
        return tokens.updateByExampleSelective(row, e).then();
    }

    @Override
    public Mono<Void> revoke(String id) {
        AnnounceRefreshToken row = new AnnounceRefreshToken();
        row.setId(id);
        row.setRevoked(true);
        row.setUpdateTime(LocalDateTime.now());
        return tokens.updateByPrimaryKeySelective(row).then();
    }
}
