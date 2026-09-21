package org.linlinjava.announce.goods.interfaces.api.goods;

import org.linlinjava.announce.goods.domain.model.agregates.LitemallGoodsProductAggregate;
import org.linlinjava.announce.goods.domain.model.valueobjects.LitemallGoodsId;

import java.util.List;

public interface LitemallGoodsProductServiceApi {
    List<LitemallGoodsProductAggregate> getByGoodsId(LitemallGoodsId goodsId);
}
