package org.linlinjava.announce.goods.application.announce;

import org.linlinjava.announce.cqrs.command.ReactiveCommandRouter;
import org.linlinjava.announce.cqrs.observability.MetaDataKeys;
import org.linlinjava.announce.goods.application.brand.BrandNotFoundException;
import org.linlinjava.announce.goods.application.category.CategoryTreeManagement;
import org.linlinjava.announce.goods.domain.announce.Announce;
import org.linlinjava.announce.goods.domain.announce.AnnounceCommands.EditAnnounce;
import org.linlinjava.announce.goods.domain.announce.AnnounceCommands.ExpireAnnounce;
import org.linlinjava.announce.goods.domain.announce.AnnounceCommands.MarkSold;
import org.linlinjava.announce.goods.domain.announce.AnnounceCommands.ModerateExpire;
import org.linlinjava.announce.goods.domain.announce.AnnounceCommands.ModerateWithdraw;
import org.linlinjava.announce.goods.domain.announce.AnnounceCommands.PostAnnounce;
import org.linlinjava.announce.goods.domain.announce.AnnounceCommands.PublishAnnounce;
import org.linlinjava.announce.goods.domain.announce.AnnounceCommands.WithdrawAnnounce;
import org.linlinjava.announce.goods.domain.announce.AnnounceContent;
import org.linlinjava.announce.goods.domain.announce.AnnounceKind;
import org.linlinjava.announce.goods.domain.announce.AnnounceReadRepository;
import org.linlinjava.announce.goods.domain.announce.GoodsDetails;
import org.linlinjava.announce.goods.domain.announce.SellerId;
import org.linlinjava.announce.goods.domain.brand.BrandRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Use cases of the announce lifecycle: post, edit, publish, withdraw, mark sold, expire. The rules live in the
 * aggregate; this facade checks the references the aggregate cannot see (category of the right kind, brand) and
 * dispatches the command through the framework router. A caller-supplied command id makes the dispatch idempotent.
 */
@Service
public class AnnouncePublishing {

    private final ReactiveCommandRouter router;
    private final CategoryTreeManagement categories;
    private final BrandRepository brands;
    private final AnnounceReadRepository readModel;
    private final Clock clock;

    public AnnouncePublishing(ReactiveCommandRouter router, CategoryTreeManagement categories, BrandRepository brands,
                              AnnounceReadRepository readModel, Clock clock) {
        this.router = router;
        this.categories = categories;
        this.brands = brands;
        this.readModel = readModel;
        this.clock = clock;
    }

    /** Creates a draft with a fresh id and returns the resulting state. */
    public Mono<Announce> post(SellerId seller, AnnounceContent content, AnnounceKind details, String commandId) {
        return checkReferences(content, details)
                .then(Mono.defer(() -> send(new PostAnnounce(UUID.randomUUID().toString(), seller, content, details), commandId)));
    }

    public Mono<Announce> edit(String id, SellerId seller, AnnounceContent content, AnnounceKind details, String commandId) {
        return checkReferences(content, details)
                .then(Mono.defer(() -> send(new EditAnnounce(id, seller, content, details), commandId)));
    }

    /** {@code validity} null means the default (60 days). */
    public Mono<Announce> publish(String id, SellerId seller, Duration validity, String commandId) {
        return send(new PublishAnnounce(id, seller, validity), commandId);
    }

    public Mono<Announce> withdraw(String id, SellerId seller, String commandId) {
        return send(new WithdrawAnnounce(id, seller), commandId);
    }

    public Mono<Announce> markSold(String id, SellerId seller, String commandId) {
        return send(new MarkSold(id, seller), commandId);
    }

    /** Staff takes an announce down (admin edge); the moderator and the reason are recorded on the event. */
    public Mono<Announce> moderateWithdraw(String id, Integer moderatorId, String reason, String commandId) {
        return send(new ModerateWithdraw(id, moderatorId, reason), commandId);
    }

    /** Staff ends an announce now, whatever its expiry date. */
    public Mono<Announce> moderateExpire(String id, Integer moderatorId, String reason, String commandId) {
        return send(new ModerateExpire(id, moderatorId, reason), commandId);
    }

    /** Expires every published announce past its date (read model lookup, one command per announce). Returns how many. */
    public Mono<Long> expireDue(int batchSize) {
        LocalDateTime now = LocalDateTime.now(clock);
        return readModel.findPublishedExpiredBefore(now, batchSize)
                .flatMap(id -> send(new ExpireAnnounce(id), null), 4)
                .count();
    }

    private Mono<Announce> send(com.opencqrs.framework.command.Command command, String commandId) {
        Map<String, ?> metaData = commandId == null || commandId.isBlank() ? Map.of() : Map.of(MetaDataKeys.COMMAND_ID, commandId);
        return router.send(command, metaData, Announce.class);
    }

    private Mono<Void> checkReferences(AnnounceContent content, AnnounceKind details) {
        if (content == null || details == null) {
            return Mono.error(new IllegalArgumentException("Content and kind-specific details are required"));
        }
        Mono<Void> category = categories.requireVisible(content.categoryId(), details.kind()).then();
        Mono<Void> brand = details instanceof GoodsDetails goods && goods.brandId() != null
                ? brands.findById(goods.brandId()).switchIfEmpty(Mono.error(() -> new BrandNotFoundException(goods.brandId()))).then()
                : Mono.empty();
        return category.then(brand);
    }
}
