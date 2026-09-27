package org.linlinjava.announce.gatewayapi.auth;

import org.linlinjava.announce.gatewayapi.storage.StorageRecords;
import org.linlinjava.announce.gatewayapi.storage.StoredFile;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** In-memory {@link UserAccounts}, {@link RefreshTokenStore} and {@link StorageRecords} so the route tests need no MySQL. */
@TestConfiguration
public class InMemoryAuthStores {

    public static final String PASSWORD = "secret123";
    public static final String USERNAME = "alice";
    public static final int USER_ID = 1;

    @Bean
    @Primary
    public UserAccounts inMemoryUserAccounts() {
        Map<Integer, Account> byId = new ConcurrentHashMap<>();
        AtomicInteger ids = new AtomicInteger(USER_ID);
        byId.put(USER_ID, new Account(USER_ID, USERNAME, new BCryptPasswordEncoder().encode(PASSWORD), "Alice", "", "", true));
        return new UserAccounts() {
            @Override
            public Mono<Account> findByLogin(String login) {
                return Mono.justOrEmpty(byId.values().stream()
                        .filter(a -> a.username().equals(login) || login.equals(a.mobile())).findFirst());
            }

            @Override
            public Mono<Account> findById(int id) {
                return Mono.justOrEmpty(byId.get(id));
            }

            @Override
            public Mono<Account> register(String username, String passwordHash, String nickname, String mobile) {
                if (byId.values().stream().anyMatch(a -> a.username().equals(username))) {
                    return Mono.error(AuthException.conflict("username already registered"));
                }
                Account a = new Account(ids.incrementAndGet(), username, passwordHash, nickname == null ? username : nickname,
                        mobile == null ? "" : mobile, "", true);
                byId.put(a.id(), a);
                return Mono.just(a);
            }

            @Override
            public Mono<Void> recordLogin(int id, String ip) {
                return Mono.empty();
            }
        };
    }

    @Bean
    @Primary
    public RefreshTokenStore inMemoryRefreshTokenStore() {
        Map<String, RefreshTokenRecord> byId = new ConcurrentHashMap<>();
        return new RefreshTokenStore() {
            @Override
            public Mono<Void> save(RefreshTokenRecord record) {
                byId.put(record.id(), record);
                return Mono.empty();
            }

            @Override
            public Mono<RefreshTokenRecord> findByHash(String tokenHash) {
                return Mono.justOrEmpty(byId.values().stream().filter(r -> r.tokenHash().equals(tokenHash)).findFirst());
            }

            @Override
            public Mono<Void> revoke(String id) {
                byId.computeIfPresent(id, (k, r) -> new RefreshTokenRecord(r.id(), r.userType(), r.userId(), r.tokenHash(), r.expiresAt(), true));
                return Mono.empty();
            }
        };
    }

    @Bean
    @Primary
    public StorageRecords inMemoryStorageRecords() {
        Map<String, StoredFile> byKey = new ConcurrentHashMap<>();
        return new StorageRecords() {
            @Override
            public Mono<Void> save(StoredFile file) {
                byKey.put(file.key(), file);
                return Mono.empty();
            }

            @Override
            public Mono<StoredFile> findByKey(String key) {
                return Mono.justOrEmpty(byKey.get(key));
            }
        };
    }
}
