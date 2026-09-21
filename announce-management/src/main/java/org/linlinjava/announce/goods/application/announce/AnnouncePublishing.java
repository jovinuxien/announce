package org.linlinjava.announce.goods.application.announce;

import org.linlinjava.announce.goods.application.announce.AnnounceCommands.ChangeAnnouncePrice;
import org.linlinjava.announce.goods.application.announce.AnnounceCommands.DraftAnnounce;
import org.linlinjava.announce.goods.application.announce.AnnounceCommands.EditAnnounce;
import org.linlinjava.announce.goods.application.announce.AnnounceCommands.MarkAnnounceSold;
import org.linlinjava.announce.goods.application.announce.AnnounceCommands.PublishAnnounce;
import org.linlinjava.announce.goods.application.announce.AnnounceCommands.WithdrawAnnounce;
import org.linlinjava.announce.goods.application.brand.BrandNotFoundException;
import org.linlinjava.announce.goods.application.category.CategoryNotFoundException;
import org.linlinjava.announce.goods.domain.announce.Announce;
import org.linlinjava.announce.goods.domain.announce.AnnounceDetails;
import org.linlinjava.announce.goods.domain.announce.AnnounceId;
import org.linlinjava.announce.goods.domain.announce.AnnounceRepository;
import org.linlinjava.announce.goods.domain.announce.SellerId;
import org.linlinjava.announce.goods.domain.brand.BrandRepository;
import org.linlinjava.announce.goods.domain.category.CategoryRepository;
import org.linlinjava.announce.goods.domain.model.events.LitemallDomainEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * Use cases of the announce lifecycle: draft, publish, edit, change price, withdraw, mark sold, expire.
 * Each one loads the aggregate, checks ownership, lets the aggregate apply the rule, saves it and publishes its events.
 */
@Service
public class AnnouncePublishing {

    private final AnnounceRepository announces;
    private final CategoryRepository categories;
    private final BrandRepository brands;
    private final LitemallDomainEventPublisher events;
    private final Clock clock;

    public AnnouncePublishing(AnnounceRepository announces, CategoryRepository categories, BrandRepository brands,
                              LitemallDomainEventPublisher events, Clock clock) {
        this.announces = announces;
        this.categories = categories;
        this.brands = brands;
        this.events = events;
        this.clock = clock;
    }

    public Mono<Announce> draft(DraftAnnounce command) {
        return checkReferences(command.details())
                .then(Mono.fromSupplier(() -> Announce.draft(command.sellerId(), command.details(), now())))
                .flatMap(this::saveAndPublish);
    }

    public Mono<Announce> publish(PublishAnnounce command) {
        return change(command.announceId(), command.sellerId(),
                a -> a.publish(command.validity() == null ? Announce.DEFAULT_VALIDITY : command.validity(), now()));
    }

    public Mono<Announce> edit(EditAnnounce command) {
        return checkReferences(command.details())
                .then(change(command.announceId(), command.sellerId(), a -> a.edit(command.details(), now())));
    }

    public Mono<Announce> changePrice(ChangeAnnouncePrice command) {
        return change(command.announceId(), command.sellerId(), a -> a.changePrice(command.price(), now()));
    }

    public Mono<Announce> withdraw(WithdrawAnnounce command) {
        return change(command.announceId(), command.sellerId(), a -> a.withdraw(now()));
    }

    public Mono<Announce> markSold(MarkAnnounceSold command) {
        return change(command.announceId(), command.sellerId(), a -> a.markSold(now()));
    }

    /** Expires every published announce past its date. Returns how many were expired. Run by the scheduler. */
    public Mono<Long> expireDue(int batchSize) {
        LocalDateTime now = now();
        return announces.findPublishedExpiredBefore(now, batchSize)
                .filter(a -> a.expireIfDue(now))
                .flatMap(this::saveAndPublish)
                .count();
    }

    /** Loads the announce for its owner (404 when unknown, 403 when someone else's). */
    public Mono<Announce> loadOwned(AnnounceId id, SellerId sellerId) {
        return announces.findById(id)
                .switchIfEmpty(Mono.error(() -> new AnnounceNotFoundException(id)))
                .flatMap(a -> a.isOwnedBy(sellerId) ? Mono.just(a) : Mono.error(new NotAnnounceOwnerException(id, sellerId)));
    }

    Mono<Announce> change(AnnounceId id, SellerId sellerId, Consumer<Announce> action) {
        return loadOwned(id, sellerId)
                .flatMap(a -> Mono.fromRunnable(() -> action.accept(a)).thenReturn(a))
                .flatMap(this::saveAndPublish);
    }

    Mono<Announce> saveAndPublish(Announce announce) {
        return announces.save(announce)
                .flatMap(saved -> Flux.fromIterable(saved.pullEvents()).doOnNext(events::publish).then(Mono.just(saved)));
    }

    private Mono<Void> checkReferences(AnnounceDetails details) {
        Mono<Void> category = categories.existsVisible(details.categoryId())
                .flatMap(exists -> exists ? Mono.empty() : Mono.error(new CategoryNotFoundException(details.categoryId())));
        Mono<Void> brand = details.brandId() == null ? Mono.empty()
                : brands.findById(details.brandId()).switchIfEmpty(Mono.error(() -> new BrandNotFoundException(details.brandId()))).then();
        return category.then(brand);
    }

    LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
