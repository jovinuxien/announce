package org.linlinjava.announce.db.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Flattened goods row produced by {@code LitemallElasticMapper.selectGoodsWithJoin}
 * and consumed by the search indexer. Property names must match the {@code elasticDataResultMap}.
 */
@Data
public class ElasticDto {
    private Integer id;
    private String goodName;
    private String brief;
    private BigDecimal counterPrice;
    private BigDecimal retailPrice;
    private String categoryName;
    private String manufacturer;
    private String attribute;
    private String parentCategory;
}
