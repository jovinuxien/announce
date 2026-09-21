package org.linlinjava.announce.goods.domain.model.repositories;

import org.linlinjava.announce.goods.domain.model.agregates.LitemallGoodsAttributeAggregate;
import org.linlinjava.announce.goods.domain.model.valueobjects.LitemallGoodsAttributeId;
import org.linlinjava.announce.goods.domain.model.valueobjects.LitemallGoodsId;

import java.util.List;

public interface AnnounceGoodsAttributeRepository {

    public void save(LitemallGoodsAttributeAggregate goodsAttributeAggregate);
    public int updateById(LitemallGoodsAttributeAggregate goodsAttributeAggregate);
    public void removeById(LitemallGoodsAttributeId goodsAttributeId);
    public void removeByGoodsId(LitemallGoodsId good);

    List<LitemallGoodsAttributeAggregate> queryByGoodsId(LitemallGoodsId goodsId);
    LitemallGoodsAttributeAggregate queryById(LitemallGoodsAttributeId id);

}
