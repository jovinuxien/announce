package org.linlinjava.announce.goods.domain.entities;


import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Getter
@Setter
@Table("announce_goods")
public class AnnounceGoodsEntity {

    @Id
    private Integer id;
    private String goodsSn;
    private String name;

    private Integer categoryId; // Foreign key to category
    private Integer brandId;    // Foreign key to brand

    private String gallery;     // JSON array of image URLs
    private String keywords;
    private String brief;
    private Boolean isOnSale;
    private Short sortOrder;
    private String picUrl;
    private String shareUrl;
    private Boolean isNew;
    private Boolean isHot;
    private String unit;
    private BigDecimal counterPrice;
    private BigDecimal retailPrice;
    private String detail;      // HTML content
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    // Transient fields for relationships
    @Transient
    private AnnounceCategoryEntity category;

    @Transient
    private AnnounceBrandEntity brand;

    @Transient
    private List<AnnounceGoodsAttributeEntity> attributes;

    @Transient
    private List<AnnounceGoodsSpecificationEntity> specifications;

    @Transient
    private List<AnnounceGoodsProductEntity> products;
}
