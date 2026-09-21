package org.linlinjava.announce.goods.infrastructure.persistence.mybatis;

import org.linlinjava.announce.db.dao.reactive.LitemallCategoryMapper;
import org.linlinjava.announce.db.domain.LitemallCategory;
import org.linlinjava.announce.db.domain.LitemallCategoryExample;
import org.linlinjava.announce.goods.domain.category.Category;
import org.linlinjava.announce.goods.domain.category.CategoryId;
import org.linlinjava.announce.goods.domain.category.CategoryRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class MyBatisCategoryRepository implements CategoryRepository {

    private final LitemallCategoryMapper categories;

    public MyBatisCategoryRepository(LitemallCategoryMapper categories) {
        this.categories = categories;
    }

    @Override
    public Mono<Category> findById(CategoryId id) {
        return categories.selectByPrimaryKey(id.value()).map(MyBatisCategoryRepository::toCategory);
    }

    @Override
    public Mono<Category> save(Category category) {
        LitemallCategory row = toRow(category);
        if (category.id() == null) {
            row.setAddTime(category.createdAt());
            return categories.insertSelective(row).then(Mono.fromRunnable(() -> category.assignId(new CategoryId(row.getId())))).thenReturn(category);
        }
        return categories.updateByPrimaryKeySelective(row).thenReturn(category);
    }

    @Override
    public Flux<Category> findRoots() {
        LitemallCategoryExample example = new LitemallCategoryExample();
        example.or().andPidEqualTo(0).andDeletedEqualTo(false);
        example.setOrderByClause("sort_order asc, id asc");
        return categories.selectByExample(example).map(MyBatisCategoryRepository::toCategory);
    }

    @Override
    public Flux<Category> findChildren(CategoryId parentId) {
        LitemallCategoryExample example = new LitemallCategoryExample();
        example.or().andPidEqualTo(parentId.value()).andDeletedEqualTo(false);
        example.setOrderByClause("sort_order asc, id asc");
        return categories.selectByExample(example).map(MyBatisCategoryRepository::toCategory);
    }

    @Override
    public Mono<Boolean> existsVisible(CategoryId id) {
        return categories.selectByPrimaryKeyWithLogicalDelete(id.value(), false).hasElement();
    }

    static Category toCategory(LitemallCategory c) {
        return Category.rehydrate(new CategoryId(c.getId()), c.getName(),
                c.getPid() == null || c.getPid() == 0 ? null : new CategoryId(c.getPid()),
                c.getKeywords(), c.getDesc(), c.getIconUrl(), c.getPicUrl(),
                c.getSortOrder() == null ? 50 : c.getSortOrder(), Boolean.TRUE.equals(c.getDeleted()), c.getAddTime(), c.getUpdateTime());
    }

    static LitemallCategory toRow(Category c) {
        LitemallCategory row = new LitemallCategory();
        row.setId(c.id() == null ? null : c.id().value());
        row.setName(c.name());
        row.setPid(c.parentId() == null ? 0 : c.parentId().value());
        row.setLevel(c.level());
        row.setKeywords(c.keywords());
        row.setDesc(c.description());
        row.setIconUrl(c.iconUrl());
        row.setPicUrl(c.pictureUrl());
        row.setSortOrder((byte) Math.max(0, Math.min(127, c.sortOrder())));
        row.setDeleted(c.isHidden());
        row.setUpdateTime(c.updatedAt());
        return row;
    }
}
