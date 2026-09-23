package org.linlinjava.announce.goods.domain.category;

import org.linlinjava.announce.goods.domain.announce.Kind;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CategoryRepository {

    Mono<Category> findById(CategoryId id);

    Mono<Category> save(Category category);

    /** Visible roots of one kind (null: every kind), by sort order. */
    Flux<Category> findRoots(Kind kind);

    /** Visible children of a root, by sort order. */
    Flux<Category> findChildren(CategoryId parentId);

    Mono<Boolean> existsVisible(CategoryId id);
}
