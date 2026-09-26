package org.linlinjava.announce.messaging.application;

import org.linlinjava.announce.db.dao.reactive.AnnounceBulletinMapper;
import org.linlinjava.announce.db.domain.AnnounceBulletin;
import org.linlinjava.announce.db.domain.AnnounceBulletinExample;
import org.linlinjava.announce.messaging.application.live.BulletinLive;
import org.linlinjava.announce.messaging.application.live.LiveFeed;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Staff bulletins (book: BulletinService): a row in {@code announce_bulletin} plus a live record on the fan-out
 * topic so every node pushes it to its connected staff sockets of the matching client type. Not event-sourced.
 */
@Service
public class Bulletins {

    public static final String AUDIENCE_STAFF = "STAFF";
    public static final String AUDIENCE_MEMBERS = "MEMBERS";

    public record BulletinView(Integer id, Integer adminId, String audience, String clientType, String content, LocalDateTime expiresAt,
                               LocalDateTime createdAt) {
        static BulletinView of(AnnounceBulletin b) {
            return new BulletinView(b.getId(), b.getAdminId(), b.getAudience(), b.getClientType(), b.getContent(), b.getExpireTime(), b.getAddTime());
        }
    }

    private final AnnounceBulletinMapper bulletins;
    private final LiveFeed live;
    private final Clock clock;

    public Bulletins(AnnounceBulletinMapper bulletins, LiveFeed live, Clock clock) {
        this.bulletins = bulletins;
        this.live = live;
        this.clock = clock;
    }

    public Mono<BulletinView> post(Integer adminId, String audience, String clientType, String content, LocalDateTime expiresAt) {
        if (adminId == null) {
            throw new IllegalArgumentException("The author is required");
        }
        if (content == null || content.isBlank() || content.length() > 1023) {
            throw new IllegalArgumentException("The bulletin text is required and at most 1023 characters");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        AnnounceBulletin row = new AnnounceBulletin();
        row.setAdminId(adminId);
        row.setAudience(audience == null || audience.isBlank() ? AUDIENCE_STAFF : audience.toUpperCase());
        row.setClientType(clientType == null || clientType.isBlank() ? null : clientType);
        row.setContent(content);
        row.setExpireTime(expiresAt);
        row.setAddTime(now);
        row.setUpdateTime(now);
        row.setDeleted(false);
        return bulletins.insertSelective(row)
                .then(Mono.fromSupplier(() -> BulletinView.of(row)))
                .flatMap(view -> live.publish("bulletin-" + view.id(),
                        new BulletinLive(view.id(), view.adminId(), view.audience(), view.clientType(), view.content(), view.expiresAt(), now)).thenReturn(view));
    }

    /** Bulletins of an audience that have not expired, newest first. */
    public Flux<BulletinView> current(String audience) {
        AnnounceBulletinExample example = new AnnounceBulletinExample();
        example.or().andAudienceEqualTo(audience == null ? AUDIENCE_STAFF : audience.toUpperCase()).andDeletedEqualTo(false).andExpireTimeIsNull();
        example.or().andAudienceEqualTo(audience == null ? AUDIENCE_STAFF : audience.toUpperCase()).andDeletedEqualTo(false)
                .andExpireTimeGreaterThan(LocalDateTime.now(clock));
        example.setOrderByClause("id desc");
        return bulletins.selectPageByExample(example, 0, 100).map(BulletinView::of);
    }
}
