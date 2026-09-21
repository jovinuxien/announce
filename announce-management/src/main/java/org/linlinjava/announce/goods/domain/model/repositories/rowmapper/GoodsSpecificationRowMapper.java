package org.linlinjava.announce.goods.domain.model.repositories.rowmapper;


import io.r2dbc.spi.Row;
import org.linlinjava.announce.goods.domain.entities.AnnounceGoodsSpecificationEntity;
import org.springframework.stereotype.Service;

import java.util.function.BiFunction;

@Service
public class GoodsSpecificationRowMapper implements BiFunction<Row, String, AnnounceGoodsSpecificationEntity> {

    private final ColumnConverter converter;

    public GoodsSpecificationRowMapper(ColumnConverter converter) {
        this.converter = converter;
    }

    @Override
    public AnnounceGoodsSpecificationEntity apply(Row row, String s) {
        AnnounceGoodsSpecificationEntity entity = new AnnounceGoodsSpecificationEntity();
        entity.setId(converter.fromRow(row, "_id", Integer.class));
        entity.setGoodsId(converter.fromRow(row, "_goods_id", Integer.class));
        entity.setSpecification(converter.fromRow(row, "_specification_name", String.class));
        entity.setValue(converter.fromRow(row, "_specification_value", String.class));
        entity.setAddTime(converter.fromRow(row, "_add_time", java.time.LocalDateTime.class));
        entity.setUpdateTime(converter.fromRow(row, "_update_time", java.time.LocalDateTime.class));
        entity.setDeleted(converter.fromRow(row, "_deleted", Boolean.class));
        return entity;
    }
}
