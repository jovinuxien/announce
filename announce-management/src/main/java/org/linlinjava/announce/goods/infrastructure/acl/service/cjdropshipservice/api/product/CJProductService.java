package org.linlinjava.announce.goods.infrastructure.acl.service.cjdropshipservice.api.product;

import org.linlinjava.announce.goods.infrastructure.acl.client.cjdropshipclient.api.product.CJProductClient;
import org.linlinjava.announce.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductDataResponse;
import org.linlinjava.announce.goods.infrastructure.acl.dto.cjdropshipdto.api.productdetail.CJProductDetailResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CJProductService {


    @Autowired
    private CJProductClient productClient;



   /* public CJProductDataResponse fetchProductList(){
        return productClient.getProductList();
    }

    public CJProductDetailResponse fetchProductById(long productId){
        return productClient.getProductById(productId);
    }*/
}
