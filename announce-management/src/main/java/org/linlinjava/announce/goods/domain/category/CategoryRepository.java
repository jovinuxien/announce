package org.linlinjava.announce.goods.domain.category;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CategoryRepository {

    Mono<Category> findById(CategoryId id);

    Mono<Category> save(Category category);

    /** Visible roots, by sort order. */
    Flux<Category> findRoots();

    /** Visible children of a root, by sort order. */
    Flux<Category> findChildren(CategoryId parentId);

    Mono<Boolean> existsVisible(CategoryId id);
}
