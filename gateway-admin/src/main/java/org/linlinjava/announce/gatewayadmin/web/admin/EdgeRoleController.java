package org.linlinjava.announce.gatewayadmin.web.admin;

import org.linlinjava.announce.db.dao.reactive.LitemallAdminMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallPermissionMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallRoleMapper;
import org.linlinjava.announce.db.domain.LitemallAdmin;
import org.linlinjava.announce.db.domain.LitemallAdminExample;
import org.linlinjava.announce.db.domain.LitemallPermission;
import org.linlinjava.announce.db.domain.LitemallPermissionExample;
import org.linlinjava.announce.db.domain.LitemallRole;
import org.linlinjava.announce.db.domain.LitemallRoleExample;
import org.springframework.http.HttpStatus;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Roles ({@code announce_role}) and their permission strings ({@code announce_permission}), {@code ROLE_SUPER} only.
 * A role that is still assigned to an administrator cannot be deleted. The permission {@code *} makes every holder
 * a super administrator; the other codes are stored for the SPA and enforced later.
 */
@RestController
@RequestMapping("/api/admin/roles")
public class EdgeRoleController {

    private final LitemallRoleMapper roles;
    private final LitemallPermissionMapper permissions;
    private final LitemallAdminMapper admins;

    public EdgeRoleController(LitemallRoleMapper roles, LitemallPermissionMapper permissions, LitemallAdminMapper admins) {
        this.roles = roles;
        this.permissions = permissions;
        this.admins = admins;
    }

    public record RoleResponse(Integer id, String name, String description, boolean enabled, LocalDateTime addTime, LocalDateTime updateTime) {
        static RoleResponse of(LitemallRole r) {
            return new RoleResponse(r.getId(), r.getName(), r.getDesc(), !Boolean.FALSE.equals(r.getEnabled()), r.getAddTime(), r.getUpdateTime());
        }
    }

    public record RoleOption(Integer id, String name) {}

    public record RoleRequest(String name, String description, Boolean enabled) {}

    public record PermissionsRequest(List<String> permissions) {}

    @GetMapping
    public Mono<PageResponse<RoleResponse>> list(@RequestParam(required = false) String q,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        Paging paging = Paging.of(page, size);
        LitemallRoleExample example = new LitemallRoleExample();
        LitemallRoleExample.Criteria c = example.or().andDeletedEqualTo(false);
        if (Paging.has(q)) {
            c.andNameLike(Paging.like(q));
        }
        example.setOrderByClause("id asc");
        return roles.countByExample(example).flatMap(total -> roles.selectPageByExample(example, paging.offset(), paging.size())
                .map(RoleResponse::of).collectList().map(items -> new PageResponse<>(items, paging.page(), paging.size(), total)));
    }

    /** Every enabled role as a picker option. */
    @GetMapping("/all")
    public Flux<RoleOption> all() {
        LitemallRoleExample example = new LitemallRoleExample();
        example.or().andDeletedEqualTo(false).andEnabledEqualTo(true);
        example.setOrderByClause("id asc");
        return roles.selectByExample(example).map(r -> new RoleOption(r.getId(), r.getName()));
    }

    @GetMapping("/{id}")
    public Mono<RoleResponse> get(@PathVariable Integer id) {
        return find(id).map(RoleResponse::of);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RoleResponse> create(@RequestBody RoleRequest body) {
        validate(body);
        return nameTaken(body.name(), null).flatMap(taken -> {
            if (taken) {
                return Mono.error(new EdgeErrors.Conflict("role-name-taken", "A role named " + body.name() + " already exists"));
            }
            LocalDateTime now = LocalDateTime.now();
            LitemallRole row = new LitemallRole();
            row.setName(body.name().trim());
            row.setDesc(body.description());
            row.setEnabled(body.enabled() == null || body.enabled());
            row.setAddTime(now);
            row.setUpdateTime(now);
            row.setDeleted(false);
            return roles.insertSelective(row).thenReturn(row);
        }).map(RoleResponse::of);
    }

    @PutMapping("/{id}")
    public Mono<RoleResponse> update(@PathVariable Integer id, @RequestBody RoleRequest body) {
        validate(body);
        return find(id).then(nameTaken(body.name(), id)).flatMap(taken -> {
            if (taken) {
                return Mono.error(new EdgeErrors.Conflict("role-name-taken", "A role named " + body.name() + " already exists"));
            }
            LitemallRole patch = new LitemallRole();
            patch.setId(id);
            patch.setName(body.name().trim());
            patch.setDesc(body.description());
            patch.setEnabled(body.enabled() == null || body.enabled());
            patch.setUpdateTime(LocalDateTime.now());
            return roles.updateByPrimaryKeySelective(patch);
        }).then(get(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable Integer id) {
        return find(id).then(assignedTo(id).collectList()).flatMap(holders -> {
            if (!holders.isEmpty()) {
                return Mono.error(new EdgeErrors.Conflict("role-in-use", "Role " + id + " is still assigned to " + holders.size() + " administrator(s)"));
            }
            LitemallPermissionExample p = new LitemallPermissionExample();
            p.or().andRoleIdEqualTo(id);
            return permissions.logicalDeleteByExample(p).then(roles.logicalDeleteByPrimaryKey(id));
        }).then();
    }

    @GetMapping("/{id}/permissions")
    public Mono<List<String>> permissions(@PathVariable Integer id) {
        return find(id).thenMany(permissionRows(id)).map(LitemallPermission::getPermission).collectList();
    }

    /** Replaces the role's permission set. */
    @PutMapping("/{id}/permissions")
    public Mono<List<String>> setPermissions(@PathVariable Integer id, @RequestBody PermissionsRequest body) {
        Set<String> wanted = new LinkedHashSet<>();
        if (body.permissions() != null) {
            body.permissions().stream().filter(Paging::has).map(String::trim).forEach(wanted::add);
        }
        for (String code : wanted) {
            if (!PermissionCatalogue.isKnown(code)) {
                return Mono.error(new IllegalArgumentException("Unknown permission " + code));
            }
        }
        return find(id).thenMany(permissionRows(id)).collectList().flatMap(existing -> {
            Set<String> current = new LinkedHashSet<>(existing.stream().map(LitemallPermission::getPermission).toList());
            LocalDateTime now = LocalDateTime.now();
            Flux<Integer> removals = Flux.fromIterable(existing).filter(p -> !wanted.contains(p.getPermission()))
                    .flatMap(p -> permissions.logicalDeleteByPrimaryKey(p.getId()));
            Flux<Integer> additions = Flux.fromIterable(wanted).filter(code -> !current.contains(code)).flatMap(code -> {
                LitemallPermission row = new LitemallPermission();
                row.setRoleId(id);
                row.setPermission(code);
                row.setAddTime(now);
                row.setUpdateTime(now);
                row.setDeleted(false);
                return permissions.insertSelective(row);
            });
            return removals.thenMany(additions).then();
        }).then(permissions(id));
    }

    private Mono<LitemallRole> find(Integer id) {
        return roles.selectByPrimaryKey(id).filter(r -> !Boolean.TRUE.equals(r.getDeleted()))
                .switchIfEmpty(Mono.error(new EdgeErrors.NotFound("Role", id)));
    }

    private Flux<LitemallPermission> permissionRows(Integer roleId) {
        LitemallPermissionExample example = new LitemallPermissionExample();
        example.or().andRoleIdEqualTo(roleId).andDeletedEqualTo(false);
        example.setOrderByClause("id asc");
        return permissions.selectByExample(example);
    }

    private Flux<LitemallAdmin> assignedTo(Integer roleId) {
        LitemallAdminExample example = new LitemallAdminExample();
        example.or().andDeletedEqualTo(false);
        return admins.selectByExample(example).filter(a -> a.getRoleIds() != null && Arrays.asList(a.getRoleIds()).contains(roleId));
    }

    private Mono<Boolean> nameTaken(String name, Integer exceptId) {
        LitemallRoleExample example = new LitemallRoleExample();
        LitemallRoleExample.Criteria c = example.or().andNameEqualTo(name.trim()).andDeletedEqualTo(false);
        if (exceptId != null) {
            c.andIdNotEqualTo(exceptId);
        }
        return roles.countByExample(example).map(n -> n > 0);
    }

    private static void validate(RoleRequest body) {
        if (body.name() == null || body.name().isBlank() || body.name().trim().length() > 63) {
            throw new IllegalArgumentException("The role name is required (at most 63 characters)");
        }
    }
}
