package org.linlinjava.announce.goods.application.brand;

import org.linlinjava.announce.goods.domain.brand.BrandId;

public class BrandNotFoundException extends RuntimeException {
    public BrandNotFoundException(BrandId id) {
        super("Brand " + id.value() + " does not exist");
    }
}
