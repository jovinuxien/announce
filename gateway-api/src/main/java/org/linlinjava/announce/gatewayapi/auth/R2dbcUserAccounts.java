package org.linlinjava.announce.gatewayapi.auth;

import org.linlinjava.announce.db.dao.reactive.LitemallUserMapper;
import org.linlinjava.announce.db.domain.LitemallUser;
import org.linlinjava.announce.db.domain.LitemallUserExample;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/** {@link UserAccounts} over the reactive {@code announce_user} mapper of db-reactive. */
@Component
public class R2dbcUserAccounts implements UserAccounts {

    private static final byte STATUS_ENABLED = 0;

    private final LitemallUserMapper users;

    public R2dbcUserAccounts(LitemallUserMapper users) {
        this.users = users;
    }

    @Override
    public Mono<Account> findByLogin(String login) {
        LitemallUserExample e = new LitemallUserExample();
        e.or().andUsernameEqualTo(login).andDeletedEqualTo(false);
        e.or().andMobileEqualTo(login).andDeletedEqualTo(false);
        return users.selectByExample(e).next().map(R2dbcUserAccounts::toAccount);
    }

    @Override
    public Mono<Account> findById(int id) {
        return users.selectByPrimaryKeyWithLogicalDelete(id, false).map(R2dbcUserAccounts::toAccount);
    }

    @Override
    public Mono<Account> register(String username, String passwordHash, String nickname, String mobile) {
        LitemallUserExample taken = new LitemallUserExample();
        taken.or().andUsernameEqualTo(username).andDeletedEqualTo(false);
        if (mobile != null && !mobile.isBlank()) taken.or().andMobileEqualTo(mobile).andDeletedEqualTo(false);
        return users.countByExample(taken)
                .flatMap(n -> n > 0
                        ? Mono.error(AuthException.conflict("username or mobile already registered"))
                        : insert(username, passwordHash, nickname, mobile));
    }

    private Mono<Account> insert(String username, String passwordHash, String nickname, String mobile) {
        LocalDateTime now = LocalDateTime.now();
        LitemallUser u = new LitemallUser();
        u.setUsername(username);
        u.setPassword(passwordHash);
        u.setNickname(nickname == null || nickname.isBlank() ? username : nickname);
        u.setMobile(mobile == null ? "" : mobile);
        u.setAvatar("");
        u.setLastLoginIp("");
        u.setWeixinOpenid("");
        u.setSessionKey("");
        u.setGender((byte) 0);
        u.setUserLevel((byte) 0);
        u.setStatus(STATUS_ENABLED);
        u.setAddTime(now);
        u.setUpdateTime(now);
        u.setDeleted(false);
        return users.insertSelective(u).then(Mono.fromSupplier(() -> toAccount(u)));
    }

    @Override
    public Mono<Void> recordLogin(int id, String ip) {
        LitemallUser u = new LitemallUser();
        u.setId(id);
        u.setLastLoginTime(LocalDateTime.now());
        u.setLastLoginIp(ip == null ? "" : ip);
        return users.updateByPrimaryKeySelective(u).then();
    }

    @Override
    public Mono<Account> updateProfile(int id, ProfileUpdate update) {
        String mobile = update.mobile() == null || update.mobile().isBlank() ? "" : update.mobile().trim();
        Mono<Long> taken = Mono.just(0L);
        if (!mobile.isEmpty()) {
            LitemallUserExample e = new LitemallUserExample();
            e.or().andMobileEqualTo(mobile).andIdNotEqualTo(id).andDeletedEqualTo(false);
            taken = users.countByExample(e);
        }
        return taken.flatMap(n -> n > 0
                        ? Mono.error(AuthException.conflict("mobile already registered"))
                        : users.selectByPrimaryKeyWithLogicalDelete(id, false))
                .switchIfEmpty(Mono.error(AuthException.unauthorized("member no longer exists")))
                .flatMap(u -> {
                    u.setNickname(update.nickname() == null || update.nickname().isBlank() ? u.getUsername() : update.nickname().trim());
                    u.setMobile(mobile);
                    u.setAvatar(update.avatar() == null ? "" : update.avatar().trim());
                    u.setGender(update.gender() == null ? (byte) 0 : update.gender().byteValue());
                    u.setBirthday(update.birthday());
                    u.setUpdateTime(LocalDateTime.now());
                    return users.updateByPrimaryKey(u).thenReturn(toAccount(u));
                });
    }

    @Override
    public Mono<Void> changePassword(int id, String passwordHash) {
        LitemallUser u = new LitemallUser();
        u.setId(id);
        u.setPassword(passwordHash);
        u.setUpdateTime(LocalDateTime.now());
        return users.updateByPrimaryKeySelective(u).then();
    }

    private static Account toAccount(LitemallUser u) {
        boolean enabled = (u.getStatus() == null || u.getStatus() == STATUS_ENABLED) && !Boolean.TRUE.equals(u.getDeleted());
        return new Account(u.getId(), u.getUsername(), u.getPassword(), u.getNickname(), u.getMobile(), u.getAvatar(), enabled,
                u.getGender() == null ? 0 : u.getGender().intValue(), u.getBirthday());
    }
}
