package org.linlinjava.announce.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.db.dao.reactive.AnnounceRefreshTokenMapper;
import org.linlinjava.announce.db.domain.AnnounceRefreshToken;
import org.linlinjava.announce.db.domain.AnnounceRefreshTokenExample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code announce_refresh_token}: the edge stores only the SHA-256 hash of a refresh token, looks it up by that hash
 * and flips {@code revoked} on rotation or logout. Ids are chosen by the edge (uuid), so there is no generated key.
 */
@SpringBootTest
class AnnounceRefreshTokenMapperTest {

    private static final String PREFIX = "it-refresh-";
    private static final LocalDateTime NOW = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

    @Autowired private AnnounceRefreshTokenMapper tokens;

    @AfterEach
    void cleanUp() {
        AnnounceRefreshTokenExample e = new AnnounceRefreshTokenExample();
        e.or().andIdLike(PREFIX + "%");
        tokens.deleteByExample(e).block();
    }

    private static AnnounceRefreshToken token(String hash) {
        AnnounceRefreshToken t = new AnnounceRefreshToken();
        t.setId(PREFIX + UUID.randomUUID().toString().substring(0, 24));
        t.setUserType("MEMBER");
        t.setUserId(1);
        t.setTokenHash(hash);
        t.setExpiresAt(NOW.plusDays(30));
        t.setRevoked(false);
        t.setAddTime(NOW);
        t.setUpdateTime(NOW);
        return t;
    }

    @Test
    void insertLookupByHashAndRevoke() {
        String hash = "f".repeat(60) + Long.toHexString(System.nanoTime() & 0xffff);
        AnnounceRefreshToken t = token(hash);
        StepVerifier.create(tokens.insert(t)).expectNext(1).verifyComplete();

        AnnounceRefreshTokenExample byHash = new AnnounceRefreshTokenExample();
        byHash.or().andTokenHashEqualTo(hash);
        StepVerifier.create(tokens.selectOneByExample(byHash))
                .assertNext(found -> {
                    assertEquals(t.getId(), found.getId());
                    assertEquals("MEMBER", found.getUserType());
                    assertEquals(NOW.plusDays(30), found.getExpiresAt());
                    assertFalse(found.getRevoked());
                })
                .verifyComplete();

        AnnounceRefreshToken revoke = new AnnounceRefreshToken();
        revoke.setId(t.getId());
        revoke.setRevoked(true);
        revoke.setUpdateTime(NOW.plusMinutes(1));
        StepVerifier.create(tokens.updateByPrimaryKeySelective(revoke)).expectNext(1).verifyComplete();
        StepVerifier.create(tokens.selectByPrimaryKey(t.getId()).map(AnnounceRefreshToken::getRevoked)).expectNext(true).verifyComplete();
    }

    @Test
    void revokeAllOfAUserByExample() {
        StepVerifier.create(tokens.insert(token("a".repeat(63) + "1"))).expectNext(1).verifyComplete();
        StepVerifier.create(tokens.insert(token("a".repeat(63) + "2"))).expectNext(1).verifyComplete();

        AnnounceRefreshTokenExample mine = new AnnounceRefreshTokenExample();
        mine.or().andIdLike(PREFIX + "%").andUserTypeEqualTo("MEMBER").andUserIdEqualTo(1).andRevokedEqualTo(false);
        AnnounceRefreshToken revoke = new AnnounceRefreshToken();
        revoke.setRevoked(true);
        StepVerifier.create(tokens.updateByExampleSelective(revoke, mine)).expectNext(2).verifyComplete();

        AnnounceRefreshTokenExample still = new AnnounceRefreshTokenExample();
        still.or().andIdLike(PREFIX + "%").andRevokedEqualTo(false);
        StepVerifier.create(tokens.countByExample(still)).assertNext(n -> assertTrue(n == 0)).verifyComplete();
    }
}
