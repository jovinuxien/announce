package org.linlinjava.announce.goods.domain.brand;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BrandRepository {

    Mono<Brand> findById(BrandId id);

    Mono<Brand> save(Brand brand);

    /** Visible brands by sort order then name; page is 1-based. */
    Flux<Brand> findVisible(int page, int size);

    Mono<Long> countVisible();

    Mono<Boolean> existsVisibleByName(String name);
}
