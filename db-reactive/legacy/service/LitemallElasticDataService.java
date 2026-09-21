package org.linlinjava.announce.db.service;


import org.linlinjava.announce.db.dao.LitemallElasticMapper;
import org.linlinjava.announce.db.dao.LitemallGoodsAttributeMapper;
import org.linlinjava.announce.db.dao.LitemallGoodsMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallCategoryMapper;
import org.linlinjava.announce.db.domain.LitemallCategory;
import org.linlinjava.announce.db.domain.LitemallGoods;
import org.linlinjava.announce.db.domain.LitemallGoodsExample;
import org.linlinjava.announce.db.dto.ElasticDto;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.Resource;
import java.util.List;

@Service
public class LitemallElasticDataService {
    @Resource
    private LitemallGoodsMapper goodsMapper;
    @Resource
    private LitemallCategoryMapper categoryMapper;
    @Resource
    private LitemallElasticMapper elasticDataMapper;
    @Resource
    private LitemallGoodsAttributeMapper goodsAttributeMapper;



    public List<LitemallGoods> getGoodsByCategory(Integer catId) {
        LitemallGoodsExample example = new LitemallGoodsExample();
        example.or().andCategoryIdEqualTo(catId);
        return  goodsMapper.selectByExample(example);
    }

    public List<LitemallGoods> getGoodsByBrand(Integer brandId) {
        LitemallGoodsExample example = new LitemallGoodsExample();
        example.or().andBrandIdEqualTo(brandId);
        return  goodsMapper.selectByExample(example);
    }



    /**
     * Category of the goods (single level for now; walking up the tree is a later step).
     * The goods mapper is still blocking, so that call runs on the bounded-elastic scheduler;
     * the category lookup is reactive.
     */
    public Flux<LitemallCategory> getCategoriesForGoods(Integer goodsId) {
        return Mono.fromCallable(() -> goodsMapper.selectByPrimaryKey(goodsId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(goods -> categoryMapper.selectByPrimaryKey(goods.getCategoryId()).flux());
    }

    public List<ElasticDto> getGoods(String manufacturerName, String attribute) {
        return elasticDataMapper.selectGoodsWithJoin(manufacturerName, attribute);
    }



}
