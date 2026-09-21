package org.linlinjava.announce.goods.domain.entities;


import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Table("announce_goods_attribute")
public class AnnounceGoodsAttributeEntity {
    @Id
    private Integer id;
    private Integer goodsId; // Foreign key to goods
    private String attribute;
    private String value;

    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    // Transient field for the parent goods
    @Transient
    private AnnounceGoodsEntity goods;
}
