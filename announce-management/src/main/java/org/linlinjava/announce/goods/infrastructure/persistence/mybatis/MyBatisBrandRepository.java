package org.linlinjava.announce.goods.infrastructure.persistence.mybatis;

import org.linlinjava.announce.db.dao.reactive.LitemallBrandMapper;
import org.linlinjava.announce.db.domain.LitemallBrand;
import org.linlinjava.announce.db.domain.LitemallBrandExample;
import org.linlinjava.announce.goods.domain.announce.Money;
import org.linlinjava.announce.goods.domain.brand.Brand;
import org.linlinjava.announce.goods.domain.brand.BrandId;
import org.linlinjava.announce.goods.domain.brand.BrandRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Repository
public class MyBatisBrandRepository implements BrandRepository {

    private final LitemallBrandMapper brands;

    public MyBatisBrandRepository(LitemallBrandMapper brands) {
        this.brands = brands;
    }

    @Override
    public Mono<Brand> findById(BrandId id) {
        return brands.selectByPrimaryKey(id.value()).map(MyBatisBrandRepository::toBrand);
    }

    @Override
    public Mono<Brand> save(Brand brand) {
        LitemallBrand row = toRow(brand);
        if (brand.id() == null) {
            row.setAddTime(brand.createdAt());
            return brands.insertSelective(row).then(Mono.fromRunnable(() -> brand.assignId(new BrandId(row.getId())))).thenReturn(brand);
        }
        return brands.updateByPrimaryKeySelective(row).thenReturn(brand);
    }

    @Override
    public Flux<Brand> findVisible(int page, int size) {
        LitemallBrandExample example = visible();
        example.setOrderByClause("sort_order asc, `name` asc");
        int p = Math.max(page, 1), s = size <= 0 ? 20 : Math.min(size, 100);
        return brands.selectPageByExample(example, (long) (p - 1) * s, s).map(MyBatisBrandRepository::toBrand);
    }

    @Override
    public Mono<Long> countVisible() {
        return brands.countByExample(visible());
    }

    @Override
    public Mono<Boolean> existsVisibleByName(String name) {
        LitemallBrandExample example = new LitemallBrandExample();
        example.or().andNameEqualTo(name).andDeletedEqualTo(false);
        return brands.countByExample(example).map(n -> n > 0);
    }

    private static LitemallBrandExample visible() {
        LitemallBrandExample example = new LitemallBrandExample();
        example.or().andDeletedEqualTo(false);
        return example;
    }

    static Brand toBrand(LitemallBrand b) {
        return Brand.rehydrate(new BrandId(b.getId()), b.getName(), b.getDesc(), b.getPicUrl(),
                b.getSortOrder() == null ? 50 : b.getSortOrder(),
                Money.of(b.getFloorPrice() == null ? BigDecimal.ZERO : b.getFloorPrice()),
                Boolean.TRUE.equals(b.getDeleted()), b.getAddTime(), b.getUpdateTime());
    }

    static LitemallBrand toRow(Brand b) {
        LitemallBrand row = new LitemallBrand();
        row.setId(b.id() == null ? null : b.id().value());
        row.setName(b.name());
        row.setDesc(b.description());
        row.setPicUrl(b.pictureUrl());
        row.setSortOrder((byte) Math.max(0, Math.min(127, b.sortOrder())));
        row.setFloorPrice(b.floorPrice().amount());
        row.setDeleted(b.isHidden());
        row.setUpdateTime(b.updatedAt());
        return row;
    }
}
