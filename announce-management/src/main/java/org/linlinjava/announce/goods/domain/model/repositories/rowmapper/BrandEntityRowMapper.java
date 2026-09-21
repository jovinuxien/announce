package org.linlinjava.announce.goods.domain.model.repositories.rowmapper;


import io.r2dbc.spi.Row;
import org.linlinjava.announce.goods.domain.entities.AnnounceBrandEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.function.BiFunction;

@Service
public class BrandEntityRowMapper implements BiFunction<Row, String, AnnounceBrandEntity> {

    private final ColumnConverter converter;

    public BrandEntityRowMapper(ColumnConverter converter) {
        this.converter = converter;
    }


    /**
     * Take a {@link Row} and a column prefix, and extract all the fields.
     * @return the {@link AnnounceBrandEntity} stored in the database.
     */
    @Override
    public AnnounceBrandEntity apply(Row row, String prefix) {
        AnnounceBrandEntity entity = new AnnounceBrandEntity();

        entity.setId(converter.fromRow(row, prefix + "_id", Integer.class));
        entity.setName(converter.fromRow(row, prefix + "_name", String.class));
        entity.setDesc(converter.fromRow(row, prefix + "_desc", String.class));
        entity.setPicUrl(converter.fromRow(row, prefix + "_pic_url", String.class));
        entity.setSortOrder(converter.fromRow(row, prefix + "_sort_order", Byte.class));
        entity.setFloorPrice(converter.fromRow(row, prefix + "_floor_price", BigDecimal.class));

        entity.setAddTime(converter.fromRow(row, prefix + "_add_time", LocalDateTime.class));
        entity.setUpdateTime(converter.fromRow(row, prefix + "_update_time", LocalDateTime.class));
        entity.setDeleted(converter.fromRow(row, prefix + "_deleted", Boolean.class));

        return null;
    }
}
