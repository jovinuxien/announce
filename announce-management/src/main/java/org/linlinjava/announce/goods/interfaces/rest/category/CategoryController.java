package org.linlinjava.announce.goods.interfaces.rest.category;

import org.linlinjava.announce.goods.application.category.CategoryTreeManagement;
import org.linlinjava.announce.goods.domain.announce.Kind;
import org.linlinjava.announce.goods.domain.category.Category;
import org.linlinjava.announce.goods.domain.category.CategoryId;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

import java.util.List;

/** Category tree API: public reads, staff writes (authorisation is enforced at the gateway for now). */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    /** {@code kind} is read on root creation only (default GOODS); children inherit it. */
    public record CategoryRequest(Kind kind, String name, String description, String iconUrl, String pictureUrl, String keywords, Integer sortOrder) {}
    public record MoveRequest(Integer parentId) {}
    public record CategoryResponse(Integer id, Kind kind, String name, Integer parentId, String level, String description, String iconUrl,
                                   String pictureUrl, String keywords, int sortOrder, boolean hidden) {
        static CategoryResponse of(Category c) {
            return new CategoryResponse(c.id().value(), c.kind(), c.name(), c.parentId() == null ? null : c.parentId().value(), c.level(),
                    c.description(), c.iconUrl(), c.pictureUrl(), c.keywords(), c.sortOrder(), c.isHidden());
        }
    }
    public record CategoryNodeResponse(CategoryResponse category, List<CategoryResponse> children) {}

    private final CategoryTreeManagement categories;

    public CategoryController(CategoryTreeManagement categories) {
        this.categories = categories;
    }

    @GetMapping
    public Flux<CategoryNodeResponse> tree(@RequestParam(required = false) Kind kind) {
        return categories.tree(kind).map(n -> new CategoryNodeResponse(CategoryResponse.of(n.category()),
                n.children().stream().map(CategoryResponse::of).toList()));
    }

    @GetMapping("/{id}")
    public Mono<CategoryResponse> get(@PathVariable Integer id) {
        return categories.get(new CategoryId(id)).map(CategoryResponse::of);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<CategoryResponse> createRoot(@RequestBody CategoryRequest body) {
        return categories.createRoot(body.kind(), body.name(), body.description(), body.iconUrl(), sortOrder(body)).map(CategoryResponse::of);
    }

    @PostMapping("/{id}/children")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<CategoryResponse> createChild(@PathVariable Integer id, @RequestBody CategoryRequest body) {
        return categories.createChild(new CategoryId(id), body.name(), body.description(), body.iconUrl(), sortOrder(body)).map(CategoryResponse::of);
    }

    @PatchMapping("/{id}")
    public Mono<CategoryResponse> update(@PathVariable Integer id, @RequestBody CategoryRequest body) {
        CategoryId cid = new CategoryId(id);
        Mono<Category> renamed = body.name() == null ? categories.get(cid) : categories.rename(cid, body.name());
        return renamed.flatMap(c -> categories.describe(cid, body.description() == null ? c.description() : body.description(),
                body.iconUrl(), body.pictureUrl(), body.keywords(), body.sortOrder() == null ? c.sortOrder() : body.sortOrder()))
                .map(CategoryResponse::of);
    }

    @PutMapping("/{id}/parent")
    public Mono<CategoryResponse> move(@PathVariable Integer id, @RequestBody MoveRequest body) {
        return categories.move(new CategoryId(id), new CategoryId(body.parentId())).map(CategoryResponse::of);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> hide(@PathVariable Integer id) {
        return categories.hide(new CategoryId(id)).then();
    }

    @PostMapping("/{id}/show")
    public Mono<CategoryResponse> show(@PathVariable Integer id) {
        return categories.show(new CategoryId(id)).map(CategoryResponse::of);
    }

    private static int sortOrder(CategoryRequest body) {
        return body.sortOrder() == null ? 50 : body.sortOrder();
    }
}
