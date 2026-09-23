package org.linlinjava.announce.goods.application.category;

import org.linlinjava.announce.goods.domain.announce.Kind;
import org.linlinjava.announce.goods.domain.category.Category;
import org.linlinjava.announce.goods.domain.category.CategoryId;
import org.linlinjava.announce.goods.domain.category.CategoryRepository;
import org.linlinjava.announce.goods.domain.model.events.LitemallDomainEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Staff use cases on the category tree. */
@Service
public class CategoryTreeManagement {

    public record CategoryNode(Category category, List<Category> children) {}

    private final CategoryRepository categories;
    private final LitemallDomainEventPublisher events;
    private final Clock clock;

    public CategoryTreeManagement(CategoryRepository categories, LitemallDomainEventPublisher events, Clock clock) {
        this.categories = categories;
        this.events = events;
        this.clock = clock;
    }

    public Mono<Category> createRoot(Kind kind, String name, String description, String iconUrl, int sortOrder) {
        return save(Category.root(kind == null ? Kind.GOODS : kind, name, description, iconUrl, sortOrder, now()));
    }

    public Mono<Category> createChild(CategoryId parentId, String name, String description, String iconUrl, int sortOrder) {
        return load(parentId).flatMap(parent -> save(Category.childOf(parent, name, description, iconUrl, sortOrder, now())));
    }

    public Mono<Category> rename(CategoryId id, String name) {
        return load(id).flatMap(c -> { c.rename(name, now()); return save(c); });
    }

    public Mono<Category> describe(CategoryId id, String description, String iconUrl, String pictureUrl, String keywords, int sortOrder) {
        return load(id).flatMap(c -> { c.describe(description, iconUrl, pictureUrl, keywords, sortOrder, now()); return save(c); });
    }

    public Mono<Category> move(CategoryId id, CategoryId newParentId) {
        return load(id).zipWith(load(newParentId))
                .flatMap(t -> { t.getT1().moveUnder(t.getT2(), now()); return save(t.getT1()); });
    }

    public Mono<Category> hide(CategoryId id) {
        return load(id).flatMap(c -> { c.hide(now()); return save(c); });
    }

    public Mono<Category> show(CategoryId id) {
        return load(id).flatMap(c -> { c.show(now()); return save(c); });
    }

    public Mono<Category> get(CategoryId id) {
        return load(id);
    }

    /** Visible roots of one kind (null: all) with their visible children. */
    public Flux<CategoryNode> tree(Kind kind) {
        return categories.findRoots(kind)
                .concatMap(root -> categories.findChildren(root.id()).collectList().map(children -> new CategoryNode(root, children)));
    }

    /** The category an announce of {@code kind} may use: it must exist, be visible and belong to that kind's tree. */
    public Mono<Category> requireVisible(CategoryId id, Kind kind) {
        return load(id).flatMap(c -> {
            if (c.isHidden()) {
                return Mono.error(new CategoryNotFoundException(id));
            }
            if (c.kind() != kind) {
                return Mono.error(new IllegalArgumentException("Category " + id.value() + " belongs to the " + c.kind() + " tree, not to " + kind));
            }
            return Mono.just(c);
        });
    }

    private Mono<Category> load(CategoryId id) {
        return categories.findById(id).switchIfEmpty(Mono.error(() -> new CategoryNotFoundException(id)));
    }

    private Mono<Category> save(Category category) {
        return categories.save(category)
                .doOnNext(saved -> saved.pullEvents().forEach(events::publish));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
