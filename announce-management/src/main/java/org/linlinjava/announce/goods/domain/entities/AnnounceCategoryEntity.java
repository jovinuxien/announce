package org.linlinjava.announce.goods.domain.entities;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Table("announce_category")
public class AnnounceCategoryEntity {

    @Id
    private Integer id;
    private String name;
    private String keywords; // JSON string, will be converted
    private String desc;
    private Integer pid; // Parent category ID
    private String iconUrl;
    private String picUrl;
    private String level;
    private Byte sortOrder;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    // Transient fields for relationships
    @Transient
    private AnnounceCategoryEntity parentCategory;

    @Transient
    private List<AnnounceCategoryEntity> subCategories;

    @Transient
    private List<AnnounceGoodsEntity> goods;
}
