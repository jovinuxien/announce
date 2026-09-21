package org.linlinjava.announce.db.service;

import org.linlinjava.announce.db.dao.reactive.LitemallCategoryMapper;
import org.linlinjava.announce.db.domain.LitemallCategory;
import org.linlinjava.announce.db.domain.LitemallCategoryExample;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Temporary reactive bridge over the migrated {@link LitemallCategoryMapper}.
 *
 * <p>Kept only so the db module compiles and the call sites can be ported; the category
 * service belongs to the goods bounded context and will be rebuilt there.
 * Paging switched from PageHelper (1-based page) to explicit offset/limit.
 */
@Service
public class LitemallCategoryService {
    @Resource
    private LitemallCategoryMapper categoryMapper;
    private final LitemallCategory.Column[] CHANNEL = {LitemallCategory.Column.id, LitemallCategory.Column.name, LitemallCategory.Column.iconUrl};

    public Flux<LitemallCategory> queryL1WithoutRecommend(int offset, int limit) {
        LitemallCategoryExample example = new LitemallCategoryExample();
        example.or().andLevelEqualTo("L1").andNameNotEqualTo("推荐").andDeletedEqualTo(false);
        return categoryMapper.selectPageByExample(example, offset, limit);
    }

    public Flux<LitemallCategory> queryL1(int offset, int limit) {
        LitemallCategoryExample example = new LitemallCategoryExample();
        example.or().andLevelEqualTo("L1").andDeletedEqualTo(false);
        return categoryMapper.selectPageByExample(example, offset, limit);
    }

    public Flux<LitemallCategory> queryL1() {
        LitemallCategoryExample example = new LitemallCategoryExample();
        example.or().andLevelEqualTo("L1").andDeletedEqualTo(false);
        return categoryMapper.selectByExample(example);
    }

    public Flux<LitemallCategory> queryByPid(Integer pid) {
        LitemallCategoryExample example = new LitemallCategoryExample();
        example.or().andPidEqualTo(pid).andDeletedEqualTo(false);
        return categoryMapper.selectByExample(example);
    }

    public Flux<LitemallCategory> queryL2ByIds(List<Integer> ids) {
        LitemallCategoryExample example = new LitemallCategoryExample();
        example.or().andIdIn(ids).andLevelEqualTo("L2").andDeletedEqualTo(false);
        return categoryMapper.selectByExample(example);
    }

    public Mono<LitemallCategory> findById(Integer id) {
        return categoryMapper.selectByPrimaryKey(id);
    }

    public Flux<LitemallCategory> querySelective(String id, String name, Integer page, Integer size, String sort, String order) {
        LitemallCategoryExample example = new LitemallCategoryExample();
        LitemallCategoryExample.Criteria criteria = example.createCriteria();

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
        int p = page == null || page < 1 ? 1 : page;
        return categoryMapper.selectPageByExample(example, (long) (p - 1) * size, size);
    }

    public Mono<Integer> updateById(LitemallCategory category) {
        category.setUpdateTime(LocalDateTime.now());
        return categoryMapper.updateByPrimaryKeySelective(category);
    }

    public Mono<Integer> deleteById(Integer id) {
        return categoryMapper.logicalDeleteByPrimaryKey(id);
    }

    /** Inserts the category; the generated id is available on {@code category} once the Mono completes. */
    public Mono<LitemallCategory> add(LitemallCategory category) {
        LocalDateTime now = LocalDateTime.now();
        category.setAddTime(now);
        category.setUpdateTime(now);
        return categoryMapper.insertSelective(category).thenReturn(category);
    }

    public Flux<LitemallCategory> queryChannel() {
        LitemallCategoryExample example = new LitemallCategoryExample();
        example.or().andLevelEqualTo("L1").andDeletedEqualTo(false);
        return categoryMapper.selectByExampleSelective(example, CHANNEL);
    }
}
