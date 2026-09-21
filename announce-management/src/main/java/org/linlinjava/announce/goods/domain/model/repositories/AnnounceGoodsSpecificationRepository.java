package org.linlinjava.announce.goods.domain.model.repositories;

import org.linlinjava.announce.goods.domain.model.agregates.LitemallGoodsSpecificationAggregate;
import org.linlinjava.announce.goods.domain.model.valueobjects.LitemallGoodsId;
import org.linlinjava.announce.goods.domain.model.valueobjects.LitemallGoodsSpecificationId;

import java.util.List;

public interface AnnounceGoodsSpecificationRepository {

    public void add(LitemallGoodsSpecificationAggregate goodsSpecificationAggregate);
    public int updateById(LitemallGoodsSpecificationAggregate goodsSpecificationAggregate);
    public void removeById(LitemallGoodsSpecificationId goodsSpecificationId);
    public void removeByGoodsId(LitemallGoodsId goodsId);

    public LitemallGoodsSpecificationAggregate findById(LitemallGoodsSpecificationId goodsSpecificationId);
    public List<LitemallGoodsSpecificationAggregate> findSpecificationByGoodsId(LitemallGoodsId goodsId);
}
