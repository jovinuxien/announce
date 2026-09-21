package org.linlinjava.announce.goods.domain.model.repositories.rowmapper;

import io.r2dbc.spi.Row;
import org.linlinjava.announce.goods.domain.entities.AnnounceCategoryEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.function.BiFunction;


@Service
public class CategoryRowMapper implements BiFunction<Row, String, AnnounceCategoryEntity> {


    private final ColumnConverter converter;

    public CategoryRowMapper(ColumnConverter converter) {
        this.converter = converter;
    }

    @Override
    public AnnounceCategoryEntity apply(Row row, String s) {
        AnnounceCategoryEntity entity = new AnnounceCategoryEntity();
        entity.setId(converter.fromRow(row, "_id", Integer.class));
        entity.setName(converter.fromRow(row, "_name", String.class));
        entity.setPid(converter.fromRow(row, "_pid", Integer.class));
        entity.setSortOrder(converter.fromRow(row, "_sort_order", Byte.class));
        entity.setPicUrl(converter.fromRow(row, "_pic_url", String.class));
        entity.setAddTime(converter.fromRow(row, "_add_time", LocalDateTime.class));
        entity.setUpdateTime(converter.fromRow(row, "_update_time", LocalDateTime.class));
        return entity;
    }
}
