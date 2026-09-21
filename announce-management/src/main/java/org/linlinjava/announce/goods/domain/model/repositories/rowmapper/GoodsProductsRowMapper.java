package org.linlinjava.announce.goods.domain.model.repositories.rowmapper;


import io.r2dbc.spi.Row;
import org.linlinjava.announce.goods.domain.entities.AnnounceGoodsProductEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.function.BiFunction;

@Service
public class GoodsProductsRowMapper implements BiFunction<Row, String, AnnounceGoodsProductEntity> {

    private final ColumnConverter converter;

    public GoodsProductsRowMapper(ColumnConverter converter) {
        this.converter = converter;
    }



    @Override
    public AnnounceGoodsProductEntity apply(Row row, String s) {
        AnnounceGoodsProductEntity entity = new AnnounceGoodsProductEntity();
        entity.setId(converter.fromRow(row, "_id", Integer.class));
        entity.setGoodsId(converter.fromRow(row, "_goods_id", Integer.class));
        entity.setPrice(converter.fromRow(row, "_price", BigDecimal.class));
        entity.setAddTime(converter.fromRow(row, "_add_time", java.time.LocalDateTime.class));
        entity.setUpdateTime(converter.fromRow(row, "_update_time", java.time.LocalDateTime.class));
        entity.setDeleted(converter.fromRow(row, "_deleted", Boolean.class));
        return entity;
    }
}
