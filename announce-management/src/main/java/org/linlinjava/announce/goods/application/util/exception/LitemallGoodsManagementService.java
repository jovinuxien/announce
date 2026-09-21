package org.linlinjava.announce.goods.application.util.exception;

import org.linlinjava.announce.goods.domain.model.agregates.LitemallGoodsAggregate;
import org.linlinjava.announce.goods.domain.model.util.dto.GoodsAllInOne;
import org.linlinjava.announce.goods.domain.model.valueobjects.LitemallGoodsId;

import java.util.List;
import java.util.Map;

public interface LitemallGoodsManagementService {

    void verifyGoodsAvailability(List<LitemallGoodsId> productIds);

    void reduceStock(Map<LitemallGoodsId, Integer> productStockMap);


    Object goodsDetail(LitemallGoodsId goodsId);

    Object addGoods(GoodsAllInOne goodsAllInOne);
    Object updateGoods(GoodsAllInOne goodsAllInOne);

    Object deleteGoods(LitemallGoodsAggregate goodsAggregate);


}
