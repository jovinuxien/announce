package org.linlinjava.announce.goods.infrastructure.acl.adapter;

import org.linlinjava.announce.goods.infrastructure.acl.model.UnifiedProduct;

import java.util.List;

public interface ProductAdapter {
    List<UnifiedProduct> adapt(List<?> sourceProducts);
}
