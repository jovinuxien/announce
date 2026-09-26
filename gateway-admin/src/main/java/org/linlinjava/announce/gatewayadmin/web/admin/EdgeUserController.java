package org.linlinjava.announce.gatewayadmin.web.admin;

import org.linlinjava.announce.db.dao.reactive.LitemallUserMapper;
import org.linlinjava.announce.db.domain.LitemallUser;
import org.linlinjava.announce.db.domain.LitemallUserExample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Members ({@code announce_user}): list, search, one member, and the status switch (0 active, 1 disabled, 2 deregistered). */
@RestController
@RequestMapping("/api/admin/users")
public class EdgeUserController {

    private final LitemallUserMapper users;

    public EdgeUserController(LitemallUserMapper users) {
        this.users = users;
    }

    public record UserResponse(Integer id, String username, String nickname, String mobile, String avatar, Byte gender, LocalDate birthday,
                               Byte userLevel, Byte status, LocalDateTime lastLoginTime, String lastLoginIp, LocalDateTime addTime) {
        static UserResponse of(LitemallUser u) {
            return new UserResponse(u.getId(), u.getUsername(), u.getNickname(), u.getMobile(), u.getAvatar(), u.getGender(), u.getBirthday(),
                    u.getUserLevel(), u.getStatus(), u.getLastLoginTime(), u.getLastLoginIp(), u.getAddTime());
        }
    }

    public record StatusRequest(Byte status) {}

    @GetMapping
    public Mono<PageResponse<UserResponse>> list(@RequestParam(required = false) String q,
                                                 @RequestParam(required = false) Byte status,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        Paging paging = Paging.of(page, size);
        LitemallUserExample example = new LitemallUserExample();
        if (Paging.has(q)) {
            String like = Paging.like(q);
            criteria(example.or().andUsernameLike(like), status);
            criteria(example.or().andNicknameLike(like), status);
            criteria(example.or().andMobileLike(like), status);
        } else {
            criteria(example.or(), status);
        }
        example.setOrderByClause("add_time desc, id desc");
        return users.countByExample(example).flatMap(total -> users.selectPageByExample(example, paging.offset(), paging.size())
                .map(UserResponse::of).collectList().map(items -> new PageResponse<>(items, paging.page(), paging.size(), total)));
    }

    private static void criteria(LitemallUserExample.Criteria c, Byte status) {
        c.andDeletedEqualTo(false);
        if (status != null) {
            c.andStatusEqualTo(status);
        }
    }

    @GetMapping("/{id}")
    public Mono<UserResponse> get(@PathVariable Integer id) {
        return users.selectByPrimaryKey(id).filter(u -> !Boolean.TRUE.equals(u.getDeleted()))
                .switchIfEmpty(Mono.error(new EdgeErrors.NotFound("Member", id))).map(UserResponse::of);
    }

    @PutMapping("/{id}/status")
    public Mono<UserResponse> setStatus(@PathVariable Integer id, @RequestBody StatusRequest body) {
        if (body.status() == null || body.status() < 0 || body.status() > 2) {
            return Mono.error(new IllegalArgumentException("status must be 0 (active), 1 (disabled) or 2 (deregistered)"));
        }
        return get(id).flatMap(existing -> {
            LitemallUser patch = new LitemallUser();
            patch.setId(id);
            patch.setStatus(body.status());
            patch.setUpdateTime(LocalDateTime.now());
            return users.updateByPrimaryKeySelective(patch);
        }).then(get(id));
    }
}
