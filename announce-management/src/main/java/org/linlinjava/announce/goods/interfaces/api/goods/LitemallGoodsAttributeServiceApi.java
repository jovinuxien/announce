package org.linlinjava.announce.goods.interfaces.api.goods;

import org.linlinjava.announce.goods.domain.model.agregates.LitemallGoodsAttributeAggregate;
import org.linlinjava.announce.goods.domain.model.valueobjects.LitemallGoodsId;

import java.util.List;

public interface LitemallGoodsAttributeServiceApi {
    List<LitemallGoodsAttributeAggregate> getByGoodsId(LitemallGoodsId goodsId);
}
