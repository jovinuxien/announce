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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void updateProfileAndChangePassword() {
        String username = PREFIX + UUID.randomUUID().toString().substring(0, 8);
        String otherName = PREFIX + UUID.randomUUID().toString().substring(0, 8);
        String otherMobile = "+46" + (System.nanoTime() % 100_000_000L);
        Account a = accounts.register(username, "$2a$10$hash", "Nick", null).block();
        accounts.register(otherName, "$2a$10$hash", "Other", otherMobile).block();
        assertNotNull(a);
        String mobile = "+45" + (System.nanoTime() % 100_000_000L);
        StepVerifier.create(accounts.updateProfile(a.id(), new UserAccounts.ProfileUpdate("Nicky", mobile, "/api/storage/fetch/k", 2, LocalDate.of(1990, 5, 1))))
                .assertNext(u -> {
                    assertEquals("Nicky", u.nickname());
                    assertEquals(mobile, u.mobile());
                    assertEquals("/api/storage/fetch/k", u.avatar());
                    assertEquals(2, u.gender());
                    assertEquals(LocalDate.of(1990, 5, 1), u.birthday());
                })
                .verifyComplete();
        StepVerifier.create(accounts.findById(a.id()).map(Account::birthday)).expectNext(LocalDate.of(1990, 5, 1)).verifyComplete();
        StepVerifier.create(accounts.findByLogin(mobile).map(Account::username)).expectNext(username).verifyComplete();
        // another member's mobile is refused; clearing works
        StepVerifier.create(accounts.updateProfile(a.id(), new UserAccounts.ProfileUpdate("Nicky", otherMobile, null, 0, null)))
                .expectErrorMatches(e -> e instanceof AuthException ae && ae.status().value() == 409).verify();
        StepVerifier.create(accounts.updateProfile(a.id(), new UserAccounts.ProfileUpdate(null, "", null, null, null)))
                .assertNext(u -> {
                    assertEquals(username, u.nickname());
                    assertEquals("", u.mobile());
                    assertNull(u.birthday());
                })
                .verifyComplete();
        StepVerifier.create(accounts.changePassword(a.id(), "$2a$10$newhash").then(accounts.findById(a.id()).map(Account::passwordHash)))
                .expectNext("$2a$10$newhash").verifyComplete();
    }

    @Test
    void revokeAllTokensOfAMember() {
        int userId = 999_999;
        String id1 = PREFIX + UUID.randomUUID().toString().substring(0, 24);
        String id2 = PREFIX + UUID.randomUUID().toString().substring(0, 24);
        String h1 = "a".repeat(56) + Long.toHexString(System.nanoTime()).substring(0, 8);
        String h2 = "b".repeat(56) + Long.toHexString(System.nanoTime() + 7).substring(0, 8);
        tokens.save(new RefreshTokenRecord(id1, "MEMBER", userId, h1, LocalDateTime.now().plusDays(1), false)).block();
        tokens.save(new RefreshTokenRecord(id2, "MEMBER", userId, h2, LocalDateTime.now().plusDays(1), false)).block();
        StepVerifier.create(tokens.revokeAllForUser("MEMBER", userId)).verifyComplete();
        StepVerifier.create(tokens.findByHash(h1).map(RefreshTokenRecord::revoked)).expectNext(true).verifyComplete();
        StepVerifier.create(tokens.findByHash(h2).map(RefreshTokenRecord::revoked)).expectNext(true).verifyComplete();
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
