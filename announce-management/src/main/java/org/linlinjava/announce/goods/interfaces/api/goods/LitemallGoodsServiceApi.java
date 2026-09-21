package org.linlinjava.announce.goods.interfaces.api.goods;

import org.linlinjava.announce.goods.domain.model.agregates.LitemallCategoryAggregate;
import org.linlinjava.announce.goods.domain.model.agregates.LitemallGoodsAggregate;
import org.linlinjava.announce.goods.domain.model.valueobjects.LitemallGoodsId;
import org.linlinjava.announce.goods.domain.model.valueobjects.category.LitemallCategoryId;
import org.linlinjava.announce.goods.domain.model.valueobjects.manufacturer.LitemallManufacturerId;

import java.util.List;

public interface LitemallGoodsServiceApi {

    /**
     * Api methods for Goods Management
     */
   /* List<LitemallGoodsAggregate> getGoodsByCategoryId(LitemallCategoryId categoryId);
    List<LitemallGoodsAggregate> getGoodsByCategoryId(LitemallCategoryId categoryId, Integer offset, Integer limit);
    int getGoodsOnSale();
    List<LitemallGoodsAggregate> getGoodsByBrand(LitemallManufacturerId brandId, String keywords, Boolean isHot, Boolean isNew, Integer page, Integer size, String sort, String order);

    LitemallGoodsAggregate getGoodsById(LitemallGoodsId goodsId);

    List<LitemallGoodsAggregate> getGoodsByHot();
    List<LitemallGoodsAggregate> getGoodsByNew();
    List<LitemallGoodsAggregate> getGoodsBySelective(LitemallCategoryId catId, LitemallManufacturerId brandId, String keywords, Boolean isHot, Boolean isNew, Integer page, Integer size, String sort);
    List<LitemallCategoryAggregate> getCatIds(Integer brandId, String keywords, Boolean isHot, Boolean isNew);*/



}
