package org.linlinjava.announce.goods.domain.entities;


import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Table("announce_goods_product")
public class AnnounceGoodsProductEntity {

    @Id
    private Integer id;
    private Integer goodsId; // Foreign key to goods
    private String specifications; // JSON array of specifications
    private BigDecimal price;
    private Integer number;
    private String url;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    // Transient field for the parent goods
    @Transient
    private AnnounceGoodsEntity goods;
}
