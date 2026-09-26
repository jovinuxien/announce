package org.linlinjava.announce.gatewayadmin.web.admin;

import org.linlinjava.announce.db.dao.reactive.LitemallAdminMapper;
import org.linlinjava.announce.db.domain.LitemallAdmin;
import org.linlinjava.announce.db.domain.LitemallAdminExample;
import org.linlinjava.announce.gatewayadmin.auth.AdminAccounts;
import org.linlinjava.announce.gatewayadmin.auth.AdminPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Staff accounts ({@code announce_admin}), {@code ROLE_SUPER} only. Passwords are BCrypt-hashed on create and on an
 * explicit change, never returned, and an administrator cannot delete their own account.
 */
@RestController
@RequestMapping("/api/admin/admins")
public class EdgeAdminAccountController {

    static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9_.-]{3,63}$");

    private final LitemallAdminMapper admins;
    private final AdminAccounts accounts;

    public EdgeAdminAccountController(LitemallAdminMapper admins, AdminAccounts accounts) {
        this.admins = admins;
        this.accounts = accounts;
    }

    public record AdminResponse(Integer id, String username, String avatar, List<Integer> roleIds, LocalDateTime lastLoginTime,
                                String lastLoginIp, LocalDateTime addTime, LocalDateTime updateTime) {
        static AdminResponse of(LitemallAdmin a) {
            return new AdminResponse(a.getId(), a.getUsername(), a.getAvatar(), a.getRoleIds() == null ? List.of() : Arrays.asList(a.getRoleIds()),
                    a.getLastLoginTime(), a.getLastLoginIp(), a.getAddTime(), a.getUpdateTime());
        }
    }

    public record AdminRequest(String username, String password, String avatar, List<Integer> roleIds) {}

    @GetMapping
    public Mono<PageResponse<AdminResponse>> list(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer size) {
        Paging paging = Paging.of(page, size);
        LitemallAdminExample example = new LitemallAdminExample();
        LitemallAdminExample.Criteria c = example.or().andDeletedEqualTo(false);
        if (Paging.has(q)) {
            c.andUsernameLike(Paging.like(q));
        }
        example.setOrderByClause("add_time desc, id desc");
        return admins.countByExample(example).flatMap(total -> admins.selectPageByExample(example, paging.offset(), paging.size())
                .map(AdminResponse::of).collectList().map(items -> new PageResponse<>(items, paging.page(), paging.size(), total)));
    }

    @GetMapping("/{id}")
    public Mono<AdminResponse> get(@PathVariable Integer id) {
        return find(id).map(AdminResponse::of);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AdminResponse> create(@RequestBody AdminRequest body) {
        validateUsername(body.username());
        if (body.password() == null || body.password().length() < 6) {
            return Mono.error(new IllegalArgumentException("The password needs at least 6 characters"));
        }
        return usernameTaken(body.username(), null).flatMap(taken -> {
            if (taken) {
                return Mono.error(new EdgeErrors.Conflict("username-taken", "An administrator named " + body.username() + " already exists"));
            }
            return encode(body.password()).flatMap(hash -> {
                LocalDateTime now = LocalDateTime.now();
                LitemallAdmin row = new LitemallAdmin();
                row.setUsername(body.username().trim());
                row.setPassword(hash);
                row.setAvatar(body.avatar() == null ? "" : body.avatar());
                row.setRoleIds(roleIds(body.roleIds()));
                row.setAddTime(now);
                row.setUpdateTime(now);
                row.setDeleted(false);
                return admins.insertSelective(row).thenReturn(row);
            });
        }).map(AdminResponse::of);
    }

    @PutMapping("/{id}")
    public Mono<AdminResponse> update(@PathVariable Integer id, @RequestBody AdminRequest body) {
        validateUsername(body.username());
        if (body.password() != null && !body.password().isEmpty() && body.password().length() < 6) {
            return Mono.error(new IllegalArgumentException("The password needs at least 6 characters"));
        }
        return find(id).then(usernameTaken(body.username(), id)).flatMap(taken -> {
            if (taken) {
                return Mono.error(new EdgeErrors.Conflict("username-taken", "An administrator named " + body.username() + " already exists"));
            }
            Mono<String> password = body.password() == null || body.password().isEmpty() ? Mono.just("") : encode(body.password());
            return password.flatMap(hash -> {
                LitemallAdmin patch = new LitemallAdmin();
                patch.setId(id);
                patch.setUsername(body.username().trim());
                patch.setAvatar(body.avatar());
                patch.setRoleIds(roleIds(body.roleIds()));
                patch.setUpdateTime(LocalDateTime.now());
                if (!hash.isEmpty()) {
                    patch.setPassword(hash);
                }
                return admins.updateByPrimaryKeySelective(patch);
            });
        }).then(get(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable Integer id, Authentication authentication) {
        AdminPrincipal me = (AdminPrincipal) authentication.getPrincipal();
        if (id.equals(me.id())) {
            return Mono.error(new EdgeErrors.Conflict("self-delete", "You cannot delete your own account"));
        }
        return find(id).flatMap(a -> admins.logicalDeleteByPrimaryKey(id)).then();
    }

    private Mono<LitemallAdmin> find(Integer id) {
        return admins.selectByPrimaryKey(id).filter(a -> !Boolean.TRUE.equals(a.getDeleted()))
                .switchIfEmpty(Mono.error(new EdgeErrors.NotFound("Administrator", id)));
    }

    private Mono<Boolean> usernameTaken(String username, Integer exceptId) {
        LitemallAdminExample example = new LitemallAdminExample();
        LitemallAdminExample.Criteria c = example.or().andUsernameEqualTo(username.trim()).andDeletedEqualTo(false);
        if (exceptId != null) {
            c.andIdNotEqualTo(exceptId);
        }
        return admins.countByExample(example).map(n -> n > 0);
    }

    private Mono<String> encode(String password) {
        return Mono.fromCallable(() -> accounts.encoder().encode(password)).subscribeOn(Schedulers.boundedElastic());
    }

    private static void validateUsername(String username) {
        if (username == null || !USERNAME.matcher(username.trim()).matches()) {
            throw new IllegalArgumentException("The username must be 3 to 63 letters, digits, '_', '.' or '-'");
        }
    }

    private static Integer[] roleIds(List<Integer> ids) {
        return ids == null ? new Integer[0] : ids.stream().filter(i -> i != null).distinct().toArray(Integer[]::new);
    }
}
