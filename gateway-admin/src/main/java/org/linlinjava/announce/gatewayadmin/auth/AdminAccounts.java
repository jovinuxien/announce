package org.linlinjava.announce.gatewayadmin.auth;

import org.linlinjava.announce.db.dao.reactive.LitemallAdminMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallPermissionMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallRoleMapper;
import org.linlinjava.announce.db.domain.LitemallAdmin;
import org.linlinjava.announce.db.domain.LitemallAdminExample;
import org.linlinjava.announce.db.domain.LitemallPermissionExample;
import org.linlinjava.announce.db.domain.LitemallRole;
import org.linlinjava.announce.db.domain.LitemallRoleExample;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Staff accounts over {@code announce_admin}, {@code announce_role} and {@code announce_permission}. Authorities:
 * {@code ROLE_ADMIN} when the account has at least one enabled, non-deleted role; {@code ROLE_SUPER} when one of
 * those roles holds the {@code *} permission (seed: role 1, admin123). BCrypt runs off the event loop.
 */
@Service
public class AdminAccounts {

    public static final String ALL_PERMISSIONS = "*";

    private final LitemallAdminMapper admins;
    private final LitemallRoleMapper roles;
    private final LitemallPermissionMapper permissions;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    public AdminAccounts(LitemallAdminMapper admins, LitemallRoleMapper roles, LitemallPermissionMapper permissions) {
        this.admins = admins;
        this.roles = roles;
        this.permissions = permissions;
    }

    public PasswordEncoder encoder() {
        return encoder;
    }

    /** Username and password to a principal; every failure is the same 401 so usernames cannot be probed. */
    public Mono<AdminPrincipal> authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            return Mono.error(new AuthenticationFailure("username and password are required"));
        }
        LitemallAdminExample example = new LitemallAdminExample();
        example.or().andUsernameEqualTo(username.trim()).andDeletedEqualTo(false);
        return admins.selectOneByExample(example)
                .switchIfEmpty(Mono.error(new AuthenticationFailure("invalid username or password")))
                .flatMap(admin -> Mono.fromCallable(() -> encoder.matches(password, admin.getPassword()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .filter(Boolean::booleanValue)
                        .switchIfEmpty(Mono.error(new AuthenticationFailure("invalid username or password")))
                        .then(principalOf(admin)));
    }

    /** Reloads the principal by id (refresh): the account must still exist and hold an enabled role. */
    public Mono<AdminPrincipal> load(Integer adminId) {
        return admins.selectByPrimaryKey(adminId)
                .filter(a -> !Boolean.TRUE.equals(a.getDeleted()))
                .switchIfEmpty(Mono.error(new AuthenticationFailure("account no longer exists")))
                .flatMap(this::principalOf);
    }

    public Mono<Void> recordLogin(Integer adminId, String ip) {
        LitemallAdmin patch = new LitemallAdmin();
        patch.setId(adminId);
        patch.setLastLoginTime(LocalDateTime.now());
        patch.setLastLoginIp(ip == null ? "" : ip);
        return admins.updateByPrimaryKeySelective(patch).then();
    }

    /** Roles to authorities; an account without any enabled role cannot log in. */
    public Mono<AdminPrincipal> principalOf(LitemallAdmin admin) {
        List<Integer> roleIds = admin.getRoleIds() == null ? List.of() : Arrays.asList(admin.getRoleIds());
        return enabledRoles(roleIds).collectList().flatMap(enabled -> {
            if (enabled.isEmpty()) {
                return Mono.error(new AuthenticationFailure("account has no enabled role"));
            }
            List<Integer> enabledIds = enabled.stream().map(LitemallRole::getId).toList();
            return hasAllPermissions(enabledIds).map(isSuper -> {
                Set<String> authorities = new LinkedHashSet<>();
                authorities.add(AdminPrincipal.ROLE_ADMIN);
                if (isSuper) {
                    authorities.add(AdminPrincipal.ROLE_SUPER);
                }
                return new AdminPrincipal(admin.getId(), admin.getUsername(), admin.getAvatar(), enabledIds, authorities);
            });
        });
    }

    private reactor.core.publisher.Flux<LitemallRole> enabledRoles(List<Integer> roleIds) {
        if (roleIds.isEmpty()) {
            return reactor.core.publisher.Flux.empty();
        }
        LitemallRoleExample example = new LitemallRoleExample();
        example.or().andIdIn(roleIds).andEnabledEqualTo(true).andDeletedEqualTo(false);
        return roles.selectByExample(example);
    }

    private Mono<Boolean> hasAllPermissions(List<Integer> roleIds) {
        LitemallPermissionExample example = new LitemallPermissionExample();
        example.or().andRoleIdIn(roleIds).andPermissionEqualTo(ALL_PERMISSIONS).andDeletedEqualTo(false);
        return permissions.countByExample(example).map(n -> n > 0);
    }
}
