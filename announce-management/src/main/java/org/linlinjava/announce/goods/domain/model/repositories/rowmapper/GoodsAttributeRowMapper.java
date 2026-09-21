package org.linlinjava.announce.goods.domain.model.repositories.rowmapper;

import io.r2dbc.spi.Row;
import org.linlinjava.announce.goods.domain.entities.AnnounceGoodsAttributeEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.function.BiFunction;


@Service
public class GoodsAttributeRowMapper implements BiFunction<Row, String, AnnounceGoodsAttributeEntity > {

    private final ColumnConverter converter;

    public GoodsAttributeRowMapper(ColumnConverter converter) {
        this.converter = converter;
    }

    @Override
    public AnnounceGoodsAttributeEntity apply(Row row, String s) {
        AnnounceGoodsAttributeEntity entity = new AnnounceGoodsAttributeEntity();
        entity.setId(converter.fromRow(row, "_id", Integer.class));
        entity.setGoodsId(converter.fromRow(row, "_goods_id", Integer.class));
        entity.setAttribute(converter.fromRow(row, "_attribute", String.class));
        entity.setValue(converter.fromRow(row, "_value", String.class));
        entity.setAddTime(converter.fromRow(row, "_add_time", LocalDateTime.class));
        entity.setUpdateTime(converter.fromRow(row, "_update_time", LocalDateTime.class));
        entity.setDeleted(converter.fromRow(row, "_deleted", Boolean.class));
        return null;
    }
}
