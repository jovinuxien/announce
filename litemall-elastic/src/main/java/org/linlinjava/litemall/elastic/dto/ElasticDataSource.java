package org.linlinjava.litemall.elastic.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ElasticDataSource {
    private String goodsName;
    private String brief;
    private BigDecimal counterPrice;
    private BigDecimal retailPrice;
    private String categoryName;
    private String manufacturer;
    private String attribute;
    private String parentCategory;
}
