package org.linlinjava.announce.gatewayapi.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.db.dao.reactive.AnnounceRefreshTokenMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallUserMapper;
import org.linlinjava.announce.db.domain.AnnounceRefreshTokenExample;
import org.linlinjava.announce.db.domain.LitemallUserExample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two R2DBC adapters against the live {@code announce_reactive_db} (like the db-reactive tests): register a
 * member, find it by username and by mobile, refuse a duplicate; store, find and revoke a refresh token.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthPersistenceTest {

    private static final String PREFIX = "it-edge-";

    @Autowired R2dbcUserAccounts accounts;
    @Autowired R2dbcRefreshTokenStore tokens;
    @Autowired LitemallUserMapper users;
    @Autowired AnnounceRefreshTokenMapper tokenRows;

    @AfterEach
    void cleanUp() {
        LitemallUserExample u = new LitemallUserExample();
        u.or().andUsernameLike(PREFIX + "%");
        users.deleteByExample(u).block();
        AnnounceRefreshTokenExample t = new AnnounceRefreshTokenExample();
        t.or().andIdLike(PREFIX + "%");
        tokenRows.deleteByExample(t).block();
    }

    @Test
    void registerFindAndRefuseDuplicate() {
        String username = PREFIX + UUID.randomUUID().toString().substring(0, 8);
        String mobile = "+45" + (System.nanoTime() % 100_000_000L);
        StepVerifier.create(accounts.register(username, "$2a$10$hash", "Nick", mobile))
                .assertNext(a -> {
                    assertTrue(a.id() > 0);
                    assertEquals("Nick", a.nickname());
                    assertTrue(a.enabled());
                })
                .verifyComplete();
        StepVerifier.create(accounts.findByLogin(username).map(Account::username)).expectNext(username).verifyComplete();
        StepVerifier.create(accounts.findByLogin(mobile).map(Account::username)).expectNext(username).verifyComplete();
        StepVerifier.create(accounts.findByLogin(username).flatMap(a -> accounts.findById(a.id())).map(Account::mobile))
                .expectNext(mobile).verifyComplete();
        StepVerifier.create(accounts.register(username, "$2a$10$hash", null, null))
                .expectErrorMatches(e -> e instanceof AuthException ae && ae.status().value() == 409)
                .verify();
        StepVerifier.create(accounts.findByLogin(username).flatMap(a -> accounts.recordLogin(a.id(), "127.0.0.1"))).verifyComplete();
    }

    @Test
    void saveFindAndRevokeRefreshToken() {
        String id = PREFIX + UUID.randomUUID().toString().substring(0, 24);
        String hash = "e".repeat(56) + Long.toHexString(System.nanoTime()).substring(0, 8);
        RefreshTokenRecord r = new RefreshTokenRecord(id, "MEMBER", 1, hash, LocalDateTime.now().plusDays(1), false);
        StepVerifier.create(tokens.save(r)).verifyComplete();
        StepVerifier.create(tokens.findByHash(hash).map(RefreshTokenRecord::revoked)).expectNext(false).verifyComplete();
        StepVerifier.create(tokens.revoke(id)).verifyComplete();
        StepVerifier.create(tokens.findByHash(hash).map(RefreshTokenRecord::revoked)).expectNext(true).verifyComplete();
        StepVerifier.create(tokens.findByHash("nope")).verifyComplete();
    }
}
