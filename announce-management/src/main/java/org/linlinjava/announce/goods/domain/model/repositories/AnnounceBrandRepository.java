package org.linlinjava.announce.goods.domain.model.repositories;

import org.linlinjava.announce.goods.domain.entities.AnnounceBrandEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Repository
public interface AnnounceBrandRepository extends R2dbcRepository<AnnounceBrandEntity, String>, BrandRepositoryInternal {

    Mono<AnnounceBrandEntity> findById(Integer id);
    Flux<AnnounceBrandEntity> findAllByDeletedFalse();
    Flux<AnnounceBrandEntity> findAllByDeletedFalse(Pageable pageable);

    @Query("DELETE FROM announce_brand WHERE id = :brandId")
    Mono<Void> deleteBrand(Long brandId);

    @Query("UPDATE announce_brand SET deleted = true WHERE id = :brandId")
    Mono<Void> softDeleteBrand(Integer brandId);

}

interface BrandRepositoryInternal{
    //Mono<AnnounceBrandEntity> saveBrand(AnnounceBrandEntity brandEntity);
}

class BrandRepositoryImpl implements BrandRepositoryInternal {

    private final DatabaseClient dbClient;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final R2dbcConverter r2dbcConverter;
    private final AnnounceGoodsRepository goodsRepository;

    public BrandRepositoryImpl(DatabaseClient dbClient, R2dbcEntityTemplate r2dbcEntityTemplate,
                               R2dbcConverter r2dbcConverter, AnnounceGoodsRepository announceGoodsRepository) {
        this.dbClient = dbClient;
        this.r2dbcEntityTemplate = r2dbcEntityTemplate;
        this.r2dbcConverter = r2dbcConverter;
        this.goodsRepository = announceGoodsRepository;
    }

   /* @Override
    public Mono<AnnounceBrandEntity> saveBrand(AnnounceBrandEntity brandEntity) {
        return r2dbcEntityTemplate.insert(AnnounceBrandEntity.class)
                .using(brandEntity)
                .defaultIfEmpty(brandEntity);
    }*/





}
