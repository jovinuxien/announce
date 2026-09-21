package org.linlinjava.announce.goods.interfaces.api.brand;

import org.linlinjava.announce.goods.domain.model.agregates.LitemallBrandAggregate;

import java.util.List;

public interface LitemallBrandServiceApi {

    /**
     * Api methods for Brand Management
     */
    List<LitemallBrandAggregate> getListBrands();
    LitemallBrandAggregate getBrandById(Long id);
}
