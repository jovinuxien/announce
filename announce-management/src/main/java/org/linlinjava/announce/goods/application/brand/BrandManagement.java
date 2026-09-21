package org.linlinjava.announce.goods.application.brand;

import org.linlinjava.announce.goods.domain.brand.Brand;
import org.linlinjava.announce.goods.domain.brand.BrandId;
import org.linlinjava.announce.goods.domain.brand.BrandRepository;
import org.linlinjava.announce.goods.domain.model.events.LitemallDomainEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDateTime;

/** Staff use cases on brands. */
@Service
public class BrandManagement {

    private final BrandRepository brands;
    private final LitemallDomainEventPublisher events;
    private final Clock clock;

    public BrandManagement(BrandRepository brands, LitemallDomainEventPublisher events, Clock clock) {
        this.brands = brands;
        this.events = events;
        this.clock = clock;
    }

    public Mono<Brand> create(String name, String description, String pictureUrl, int sortOrder) {
        return brands.existsVisibleByName(name == null ? "" : name.trim())
                .flatMap(exists -> exists
                        ? Mono.error(new IllegalArgumentException("A brand named '" + name + "' already exists"))
                        : save(Brand.create(name, description, pictureUrl, sortOrder, LocalDateTime.now(clock))));
    }

    public Mono<Brand> rename(BrandId id, String name, String description, String pictureUrl, int sortOrder) {
        return load(id).flatMap(b -> { b.rename(name, description, pictureUrl, sortOrder, LocalDateTime.now(clock)); return save(b); });
    }

    public Mono<Brand> hide(BrandId id) {
        return load(id).flatMap(b -> { b.hide(LocalDateTime.now(clock)); return save(b); });
    }

    public Mono<Brand> get(BrandId id) {
        return load(id);
    }

    public Flux<Brand> list(int page, int size) {
        return brands.findVisible(page, size);
    }

    public Mono<Long> count() {
        return brands.countVisible();
    }

    private Mono<Brand> load(BrandId id) {
        return brands.findById(id).switchIfEmpty(Mono.error(() -> new BrandNotFoundException(id)));
    }

    private Mono<Brand> save(Brand brand) {
        return brands.save(brand).doOnNext(saved -> saved.pullEvents().forEach(events::publish));
    }
}
