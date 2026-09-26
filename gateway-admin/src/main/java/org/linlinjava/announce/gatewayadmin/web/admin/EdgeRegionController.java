package org.linlinjava.announce.gatewayadmin.web.admin;

import org.linlinjava.announce.db.dao.reactive.LitemallRegionMapper;
import org.linlinjava.announce.db.domain.LitemallRegion;
import org.linlinjava.announce.db.domain.LitemallRegionExample;
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

/**
 * Regions ({@code announce_region}: {@code pid} parent, {@code type} 1 country/state, 2 city/county, 3 area). The
 * table has no soft-delete column; a region with children cannot be removed.
 */
@RestController
@RequestMapping("/api/admin/regions")
public class EdgeRegionController {

    private final LitemallRegionMapper regions;

    public EdgeRegionController(LitemallRegionMapper regions) {
        this.regions = regions;
    }

    public record RegionResponse(Integer id, Integer pid, String name, Byte type, Integer code) {
        static RegionResponse of(LitemallRegion r) {
            return new RegionResponse(r.getId(), r.getPid(), r.getName(), r.getType(), r.getCode());
        }
    }

    public record RegionRequest(Integer pid, String name, Byte type, Integer code) {}

    @GetMapping
    public Mono<PageResponse<RegionResponse>> list(@RequestParam(required = false) Integer pid,
                                                   @RequestParam(required = false) Byte type,
                                                   @RequestParam(required = false) String q,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size) {
        Paging paging = Paging.of(page, size);
        LitemallRegionExample example = new LitemallRegionExample();
        LitemallRegionExample.Criteria c = example.or();
        if (pid != null) {
            c.andPidEqualTo(pid);
        }
        if (type != null) {
            c.andTypeEqualTo(type);
        }
        if (Paging.has(q)) {
            c.andNameLike(Paging.like(q));
        }
        example.setOrderByClause("type asc, name asc");
        return regions.countByExample(example).flatMap(total -> regions.selectPageByExample(example, paging.offset(), paging.size())
                .map(RegionResponse::of).collectList().map(items -> new PageResponse<>(items, paging.page(), paging.size(), total)));
    }

    @GetMapping("/{id}")
    public Mono<RegionResponse> get(@PathVariable Integer id) {
        return find(id).map(RegionResponse::of);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RegionResponse> create(@RequestBody RegionRequest body) {
        validate(body);
        LitemallRegion row = new LitemallRegion();
        row.setPid(body.pid() == null ? 0 : body.pid());
        row.setName(body.name().trim());
        row.setType(body.type());
        row.setCode(body.code() == null ? 0 : body.code());
        return regions.insertSelective(row).thenReturn(row).map(RegionResponse::of);
    }

    @PutMapping("/{id}")
    public Mono<RegionResponse> update(@PathVariable Integer id, @RequestBody RegionRequest body) {
        validate(body);
        return find(id).flatMap(existing -> {
            LitemallRegion patch = new LitemallRegion();
            patch.setId(id);
            patch.setPid(body.pid() == null ? existing.getPid() : body.pid());
            patch.setName(body.name().trim());
            patch.setType(body.type());
            patch.setCode(body.code() == null ? existing.getCode() : body.code());
            return regions.updateByPrimaryKeySelective(patch);
        }).then(get(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable Integer id) {
        LitemallRegionExample children = new LitemallRegionExample();
        children.or().andPidEqualTo(id);
        return find(id).then(regions.countByExample(children)).flatMap(n -> {
            if (n > 0) {
                return Mono.error(new EdgeErrors.Conflict("region-has-children", "Region " + id + " still has " + n + " child region(s)"));
            }
            return regions.deleteByPrimaryKey(id);
        }).then();
    }

    private Mono<LitemallRegion> find(Integer id) {
        return regions.selectByPrimaryKey(id).switchIfEmpty(Mono.error(new EdgeErrors.NotFound("Region", id)));
    }

    private static void validate(RegionRequest body) {
        if (body.name() == null || body.name().isBlank() || body.name().trim().length() > 120) {
            throw new IllegalArgumentException("The region name is required (at most 120 characters)");
        }
        if (body.type() == null || body.type() < 1 || body.type() > 3) {
            throw new IllegalArgumentException("type must be 1 (country/state), 2 (city/county) or 3 (area)");
        }
    }
}
