package org.linlinjava.announce.goods.interfaces.rest.brand;

import org.linlinjava.announce.goods.application.brand.BrandManagement;
import org.linlinjava.announce.goods.domain.brand.Brand;
import org.linlinjava.announce.goods.domain.brand.BrandId;
import org.linlinjava.announce.goods.interfaces.rest.announce.AnnounceDtos.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/brands")
public class BrandController {

    public record BrandRequest(String name, String description, String pictureUrl, Integer sortOrder) {}
    public record BrandResponse(Integer id, String name, String description, String pictureUrl, int sortOrder, BigDecimal floorPrice, boolean hidden) {
        static BrandResponse of(Brand b) {
            return new BrandResponse(b.id().value(), b.name(), b.description(), b.pictureUrl(), b.sortOrder(), b.floorPrice().amount(), b.isHidden());
        }
    }

    private final BrandManagement brands;

    public BrandController(BrandManagement brands) {
        this.brands = brands;
    }

    @GetMapping
    public Mono<PageResponse<BrandResponse>> list(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return brands.count().flatMap(total -> brands.list(page, size).map(BrandResponse::of).collectList()
                .map(items -> new PageResponse<>(items, Math.max(page, 1), size, total)));
    }

    @GetMapping("/{id}")
    public Mono<BrandResponse> get(@PathVariable Integer id) {
        return brands.get(new BrandId(id)).map(BrandResponse::of);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<BrandResponse> create(@RequestBody BrandRequest body) {
        return brands.create(body.name(), body.description(), body.pictureUrl(), body.sortOrder() == null ? 50 : body.sortOrder()).map(BrandResponse::of);
    }

    @PutMapping("/{id}")
    public Mono<BrandResponse> update(@PathVariable Integer id, @RequestBody BrandRequest body) {
        return brands.rename(new BrandId(id), body.name(), body.description(), body.pictureUrl(), body.sortOrder() == null ? 50 : body.sortOrder()).map(BrandResponse::of);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> hide(@PathVariable Integer id) {
        return brands.hide(new BrandId(id)).then();
    }
}
