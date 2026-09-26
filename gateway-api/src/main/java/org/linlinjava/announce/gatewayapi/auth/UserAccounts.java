package org.linlinjava.announce.gatewayapi.auth;

import reactor.core.publisher.Mono;

/** Port over the member store ({@code announce_user}); the R2DBC implementation is {@link R2dbcUserAccounts}. */
public interface UserAccounts {

    /** The member whose username or mobile equals {@code login}; empty when unknown. */
    Mono<Account> findByLogin(String login);

    Mono<Account> findById(int id);

    /** Creates the member; errors with {@link AuthException} (409) when the username or mobile is taken. */
    Mono<Account> register(String username, String passwordHash, String nickname, String mobile);

    Mono<Void> recordLogin(int id, String ip);
}
