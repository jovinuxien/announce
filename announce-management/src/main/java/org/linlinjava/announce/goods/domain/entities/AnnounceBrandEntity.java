package org.linlinjava.announce.goods.domain.entities;


import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Table("announce_brand")
public class AnnounceBrandEntity {

    @Id
    private Integer id;
    private String name;
    private String desc;
    private String picUrl;
    private Byte sortOrder;
    private BigDecimal floorPrice;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    @Transient
    private List<AnnounceGoodsEntity> goodsList;
}
