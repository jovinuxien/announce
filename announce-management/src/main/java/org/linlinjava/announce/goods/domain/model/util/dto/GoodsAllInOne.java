package org.linlinjava.announce.goods.domain.model.util.dto;

import org.linlinjava.announce.goods.domain.model.agregates.LitemallGoodsAggregate;
import org.linlinjava.announce.goods.domain.model.agregates.LitemallGoodsAttributeAggregate;
import org.linlinjava.announce.goods.domain.model.agregates.LitemallGoodsProductAggregate;
import org.linlinjava.announce.goods.domain.model.agregates.LitemallGoodsSpecificationAggregate;

import java.util.List;

public class GoodsAllInOne {

    LitemallGoodsAggregate goodsAggregate;
    List<LitemallGoodsSpecificationAggregate> specificationAggregates;
    List<LitemallGoodsAttributeAggregate> attributesAggregates;
    List<LitemallGoodsProductAggregate> productsAggregates;

    public LitemallGoodsAggregate getGoods() {
        return goodsAggregate;
    }

    public void setGoods(LitemallGoodsAggregate goods) {
        this.goodsAggregate = goods;
    }

    public List<LitemallGoodsProductAggregate> getProducts() {
        return productsAggregates;
    }

    public void setProducts(List<LitemallGoodsProductAggregate> products) {
        this.productsAggregates = products;
    }

    public List<LitemallGoodsSpecificationAggregate> getSpecifications() {
        return specificationAggregates;
    }

    public void setSpecifications(List<LitemallGoodsSpecificationAggregate> specifications) {
        this.specificationAggregates = specifications;
    }

    public List<LitemallGoodsAttributeAggregate> getAttributes() {
        return attributesAggregates;
    }

    public void setAttributes(List<LitemallGoodsAttributeAggregate> attributes) {
        this.attributesAggregates = attributes;
    }

}
