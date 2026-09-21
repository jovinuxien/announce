package org.linlinjava.announce.goods.domain.model.repositories;


import org.linlinjava.announce.goods.domain.model.agregates.LitemallGoodsProductAggregate;
import org.linlinjava.announce.goods.domain.model.valueobjects.LitemallGoodsId;
import org.linlinjava.announce.goods.domain.model.valueobjects.LitemallGoodsProductId;

import java.util.List;
import java.util.Optional;

public interface AnnounceGoodsProductRepository {


    Optional<LitemallGoodsProductAggregate> findById(LitemallGoodsProductId goodsProductId);
    List<LitemallGoodsProductAggregate> findByGoodsId(LitemallGoodsId goodsId);

    void deleteById(LitemallGoodsProductId goodsProductId);
    void deleteByGoodsId(LitemallGoodsId goodsId);

    void addGoodsProduct(LitemallGoodsProductAggregate productAggregate);
    void updateGoodsById(LitemallGoodsProductAggregate goodsProductAggregate);
    int count();

    boolean isStockEnough(LitemallGoodsProductId productId, Short number);
    int reduceStock(LitemallGoodsProductId productId, Short number);
    int addStock(LitemallGoodsProductId productId, Short number);

}

