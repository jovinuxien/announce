package org.linlinjava.announce.goods.domain.model.repositories;


import org.linlinjava.announce.goods.domain.entities.AnnounceGoodsEntity;
import org.linlinjava.announce.goods.domain.model.agregates.LitemallGoodsAggregate;
import org.linlinjava.announce.goods.domain.model.valueobjects.LitemallGoodsId;
import org.linlinjava.announce.goods.domain.model.valueobjects.category.LitemallCategoryId;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
public interface AnnounceGoodsRepository extends R2dbcRepository<AnnounceGoodsEntity, Integer> {

    /*public void addGoods(LitemallGoodsAggregate goodsAggregate);
    public Mono<Long> count();
    public int updateById(LitemallGoodsAggregate goodsAggregate);
    public void deleteById(LitemallGoodsId goodsId);*/



    Flux<AnnounceGoodsEntity> findByBrandIdAndDeletedFalse(Integer brandId);

    Flux<AnnounceGoodsEntity> findByCategoryIdAndDeletedFalse(Integer categoryId);

    Flux<AnnounceGoodsEntity> findByIsOnSaleAndDeletedFalse(Boolean isOnSale);

    Flux<AnnounceGoodsEntity> findByBrandIdAndCategoryIdAndDeletedFalse(Integer brandId, Integer categoryId);




    Mono<AnnounceGoodsEntity> findById(LitemallGoodsId goodsId);


    //public Flux<AnnounceGoodsEntity> queryByIds(List<LitemallGoodsId> ids);
    //public Flux<AnnounceGoodsEntity> queryByCategories(List<Integer> catList, int offset, int limit);
    //public Flux<Integer> getCategoryIds(Integer brandId, String keywords, Boolean isHot, Boolean isNew);
    //public Mono<Boolean> checkExistByName(String name);

    @org.springframework.data.r2dbc.repository.Query("SELECT * FROM announce_goods WHERE brand_id = :brandId AND deleted = false")
    Flux<AnnounceGoodsEntity> findGoodsByBrand(Integer brandId);
    @org.springframework.data.r2dbc.repository.Query("SELECT * FROM announce_goods WHERE brand_id = :brandId AND is_on_sale = true AND deleted = false")
    Flux<AnnounceGoodsEntity> findActiveGoodsByBrand(Integer brandId);
    @org.springframework.data.r2dbc.repository.Query("SELECT *  FROM announce_goods WHERE is_on_sale = true AND deleted = false")
    public Flux<AnnounceGoodsEntity> queryOnSale();
    @org.springframework.data.r2dbc.repository.Query("SELECT *  FROM announce_goods WHERE brand_id = :brandId AND deleted = false")
    public Flux<AnnounceGoodsEntity> queryByBrand(Integer manufacturerId, int offset, int limit);
    @org.springframework.data.r2dbc.repository.Query("SELECT *  FROM announce_goods WHERE brand_id = :brandId AND on_sale = true AND deleted = false")
    public Flux<AnnounceGoodsEntity> queryActiveByBrand(Integer manufacturerId, int offset, int limit);
    @org.springframework.data.r2dbc.repository.Query("SELECT *  FROM announce_goods WHERE category_id = :categoryId AND deleted = false")
    public Flux<AnnounceGoodsEntity> queryByCategory(Integer categoryId, int offset, int limit);

}

interface GoodsRepositoryInternal {
    Mono<AnnounceGoodsEntity> saveGoods(AnnounceGoodsEntity goodsEntity);
    Mono<AnnounceGoodsEntity> findByIdWithRelations(Integer id);
    Flux<AnnounceGoodsEntity> findByBrandIdWithRelations(Integer brandId);
    public Flux<AnnounceGoodsEntity> queryByCategoryAndBrand(Integer catId, Integer brandId, Boolean isHot, Boolean isNew, Integer offset, Integer limit);


    public Flux<AnnounceGoodsEntity> queryHotGoods(int offset, int limit);
    public Flux<AnnounceGoodsEntity> queryNewGoods(int offset, int limit);
}

class GoodsRepositoryImpl implements GoodsRepositoryInternal {

    private final DatabaseClient dbClient;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final R2dbcConverter r2dbcConverter;
    private final AnnounceBrandRepository brandRepository;
    private final AnnounceCategoryRepository categoryRepository;

    public GoodsRepositoryImpl (
            DatabaseClient dbClient,
            R2dbcEntityTemplate r2dbcEntityTemplate,
            R2dbcConverter r2dbcConverter,
            AnnounceBrandRepository brandRepository,
            AnnounceCategoryRepository categoryRepository
    ){
        this.dbClient = dbClient;
        this.r2dbcEntityTemplate = r2dbcEntityTemplate;
        this.r2dbcConverter = r2dbcConverter;
        this.brandRepository = brandRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public Mono<AnnounceGoodsEntity> saveGoods(AnnounceGoodsEntity goodsEntity) {
        return r2dbcEntityTemplate.insert(AnnounceGoodsEntity.class)
                .using(goodsEntity)
                .defaultIfEmpty(goodsEntity);
    }

    @Override
    public Mono<AnnounceGoodsEntity> findByIdWithRelations(Integer id) {
        return r2dbcEntityTemplate.selectOne(
                Query.query(Criteria.where("id").is(id).and("deleted").is(false)),
                 AnnounceGoodsEntity.class
                )
                .flatMap(goods ->
                        // Load brand and category relationships
                        Mono.zip(
                                brandRepository.findById(goods.getBrandId()),
                                categoryRepository.findByIdAndDeletedFalse(goods.getCategoryId())
                        )
                        .map(tuple -> {
                            goods.setBrand(tuple.getT1());
                            goods.setCategory(tuple.getT2());
                            return goods;
                        })
                );
    }

    @Override
    public Flux<AnnounceGoodsEntity> findByBrandIdWithRelations(Integer brandId) {
        return r2dbcEntityTemplate.select(
                Query.query(Criteria.where("brand_id").is(brandId).and("deleted").is(false)),
                AnnounceGoodsEntity.class
                )
                .flatMap(goods ->
                        // Load category for each goods
                         categoryRepository.findByIdAndDeletedFalse(goods.getCategoryId())
                        .map(category -> {
                          goods.setCategory(category);
                          return goods;
                        })
                );
    }

    @Override
    public Flux<AnnounceGoodsEntity> queryByCategoryAndBrand(Integer catId, Integer brandId, Boolean isHot, Boolean isNew, Integer offset, Integer limit) {
        return  r2dbcEntityTemplate.select(
                Query.query(Criteria.where("category_id").is(catId)
                        .and("brand_id").is(brandId)
                        .and("is_hot").isTrue()
                        .and ("is_new").isTrue()
                        .and("deleted").is(false))
                        .offset(offset)
                        .limit(limit),
                AnnounceGoodsEntity.class);
    }

    @Override
    public Flux<AnnounceGoodsEntity> queryHotGoods(int offset, int limit) {
        return r2dbcEntityTemplate.select(
                Query.query(Criteria.where("is_hot").is(true).and("deleted").is(false)),
                AnnounceGoodsEntity.class);
    }

    @Override
    public Flux<AnnounceGoodsEntity> queryNewGoods(int offset, int limit) {
        return r2dbcEntityTemplate.select(
                Query.query(Criteria.where("is_new").is(true).and("deleted").is(false)),
                AnnounceGoodsEntity.class);
    }
}
