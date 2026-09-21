package org.linlinjava.announce.goods.domain.model.repositories;

import org.linlinjava.announce.goods.domain.entities.AnnounceCategoryEntity;
import org.linlinjava.announce.goods.domain.model.agregates.LitemallCategoryAggregate;
import org.linlinjava.announce.goods.domain.model.valueobjects.category.LitemallCategoryId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
public interface AnnounceCategoryRepository extends R2dbcRepository<AnnounceCategoryEntity, Integer>, CategoryRepositoryInternal {


    // Basic queries
    Mono<AnnounceCategoryEntity> findByIdAndDeletedFalse(Integer id);

    Flux<AnnounceCategoryEntity> findByPidAndDeletedFalse(Integer pid);

    Flux<AnnounceCategoryEntity> findByLevelAndDeletedFalse(String level);

    Flux<AnnounceCategoryEntity> findByLevelAndDeletedFalse(String level, Pageable pageable);

    Flux<AnnounceCategoryEntity> findByIdInAndDeletedFalse(List<Integer> ids);

    // Custom query methods for your specific requirements
    @Query("SELECT * FROM announce_category WHERE level = 'L1' AND deleted = false ORDER BY sort_order ASC, id ASC LIMIT :limit OFFSET :offset")
    Flux<AnnounceCategoryEntity> queryL1WithPagination(int offset, int limit);

    @Query("SELECT * FROM announce_category WHERE level = 'L1' AND name NOT LIKE '%recommend%' AND deleted = false ORDER BY sort_order ASC, id ASC LIMIT :limit OFFSET :offset")
    Flux<AnnounceCategoryEntity> queryL1WithoutRecommendWithPagination(int offset, int limit);

    @Query("SELECT * FROM announce_category WHERE pid = :pid AND deleted = false ORDER BY sort_order ASC, id ASC")
    Flux<AnnounceCategoryEntity> queryByPid(Integer pid);

    @Query("SELECT * FROM announce_category WHERE id IN (:ids) AND level = 'L2' AND deleted = false ORDER BY sort_order ASC, id ASC")
    Flux<AnnounceCategoryEntity> queryL2ByIds(List<Integer> ids);


}

interface CategoryRepositoryInternal{
    Mono<AnnounceCategoryEntity> saveCategory(AnnounceCategoryEntity category);
    Flux<AnnounceCategoryEntity> queryL1();
    Flux<AnnounceCategoryEntity> queryL1(int offset, int limit);
    Flux<AnnounceCategoryEntity> queryL1WithoutRecommend(int offset, int limit);
    Flux<AnnounceCategoryEntity> queryL1WithoutRecommend();
    Mono<AnnounceCategoryEntity> findByIdWithRelations(Integer id);
    Flux<AnnounceCategoryEntity> queryByPidWithRelations(Integer pid);
    Flux<AnnounceCategoryEntity> queryL2ByIdsWithRelations(List<Integer> ids);
}

class CategoryRepositoryInternalImpl implements CategoryRepositoryInternal {


    private final DatabaseClient dbClient;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final R2dbcConverter r2dbcConverter;
    private final AnnounceGoodsRepository goodsRepository;

    public CategoryRepositoryInternalImpl(
            DatabaseClient dbClient,
            R2dbcEntityTemplate r2dbcEntityTemplate,
            R2dbcConverter r2dbcConverter,
            AnnounceGoodsRepository goodsRepository) {
        this.dbClient = dbClient;
        this.r2dbcEntityTemplate = r2dbcEntityTemplate;
        this.r2dbcConverter = r2dbcConverter;
        this.goodsRepository = goodsRepository;
    }
    @Override
    public Mono<AnnounceCategoryEntity> saveCategory(AnnounceCategoryEntity category) {
       return r2dbcEntityTemplate.insert(AnnounceCategoryEntity.class).using(category).defaultIfEmpty(category);
    }

    @Override
    public Flux<AnnounceCategoryEntity> queryL1() {
        return r2dbcEntityTemplate.select(
                org.springframework.data.relational.core.query.Query.query(
                        Criteria.where("level").is("L1")
                                .and("deleted").is(false)),
                AnnounceCategoryEntity.class);
    }

    @Override
    public Flux<AnnounceCategoryEntity> queryL1(int offset, int limit) {
        return r2dbcEntityTemplate.select(
                org.springframework.data.relational.core.query.Query.query(
                        Criteria.where("level").is("L1")
                                .and("deleted").is(false))
                        .sort(Sort.by("sort_order", "id"))
                        .limit(limit)
                        .offset(offset),
                AnnounceCategoryEntity.class);
    }

    @Override
    public Flux<AnnounceCategoryEntity> queryL1WithoutRecommend(int offset, int limit) {
        return null;
    }

    @Override
    public Flux<AnnounceCategoryEntity> queryL1WithoutRecommend() {
        return null;
    }

    @Override
    public Mono<AnnounceCategoryEntity> findByIdWithRelations(Integer id) {
        return null;
    }

    @Override
    public Flux<AnnounceCategoryEntity> queryByPidWithRelations(Integer pid) {
        return null;
    }

    @Override
    public Flux<AnnounceCategoryEntity> queryL2ByIdsWithRelations(List<Integer> ids) {
        return null;
    }
}
