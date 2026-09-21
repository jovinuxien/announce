package org.linlinjava.announce.db.service;

import org.linlinjava.announce.db.dao.reactive.LitemallBrandMapper;
import org.linlinjava.announce.db.domain.LitemallBrand;
import org.linlinjava.announce.db.domain.LitemallBrand.Column;
import org.linlinjava.announce.db.domain.LitemallBrandExample;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;

/**
 * Temporary reactive bridge over the migrated {@link LitemallBrandMapper}.
 *
 * <p>Kept only so the db module compiles and so the call sites can be ported. The real brand
 * (manufacturer) service belongs to the goods bounded context and will be rebuilt there.
 * Paging switched from PageHelper (1-based page) to explicit offset/limit.
 */
@Service
public class LitemallBrandService {
    @Resource
    private LitemallBrandMapper brandMapper;
    private final Column[] columns = new Column[]{Column.id, Column.name, Column.desc, Column.picUrl, Column.floorPrice};

    public Flux<LitemallBrand> query(Integer page, Integer limit, String sort, String order) {
        LitemallBrandExample example = new LitemallBrandExample();
        example.or().andDeletedEqualTo(false);
        if (StringUtils.hasText(sort) && StringUtils.hasText(order)) {
            example.setOrderByClause(sort + " " + order);
        }
        return brandMapper.selectPageByExampleSelective(example, offset(page, limit), limit, columns);
    }

    public Flux<LitemallBrand> query(Integer page, Integer limit) {
        return query(page, limit, null, null);
    }

    public Mono<LitemallBrand> findById(Integer id) {
        return brandMapper.selectByPrimaryKey(id);
    }

    public Flux<LitemallBrand> querySelective(String id, String name, Integer page, Integer size, String sort, String order) {
        LitemallBrandExample example = new LitemallBrandExample();
        LitemallBrandExample.Criteria criteria = example.createCriteria();

        if (StringUtils.hasText(id)) {
            criteria.andIdEqualTo(Integer.valueOf(id));
        }
        if (StringUtils.hasText(name)) {
            criteria.andNameLike("%" + name + "%");
        }
        criteria.andDeletedEqualTo(false);

        if (StringUtils.hasText(sort) && StringUtils.hasText(order)) {
            example.setOrderByClause(sort + " " + order);
        }
        return brandMapper.selectPageByExample(example, offset(page, size), size);
    }

    public Mono<Integer> updateById(LitemallBrand brand) {
        brand.setUpdateTime(LocalDateTime.now());
        return brandMapper.updateByPrimaryKeySelective(brand);
    }

    public Mono<Integer> deleteById(Integer id) {
        return brandMapper.logicalDeleteByPrimaryKey(id);
    }

    /** Inserts the brand; the generated id is available on {@code brand} once the Mono completes. */
    public Mono<LitemallBrand> add(LitemallBrand brand) {
        LocalDateTime now = LocalDateTime.now();
        brand.setAddTime(now);
        brand.setUpdateTime(now);
        return brandMapper.insertSelective(brand).thenReturn(brand);
    }

    public Flux<LitemallBrand> all() {
        LitemallBrandExample example = new LitemallBrandExample();
        example.or().andDeletedEqualTo(false);
        return brandMapper.selectByExample(example);
    }

    private static long offset(Integer page, Integer limit) {
        int p = page == null || page < 1 ? 1 : page;
        return (long) (p - 1) * limit;
    }
}
