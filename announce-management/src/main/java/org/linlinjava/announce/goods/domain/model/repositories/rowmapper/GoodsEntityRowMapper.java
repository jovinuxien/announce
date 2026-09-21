package org.linlinjava.announce.goods.domain.model.repositories.rowmapper;

import io.r2dbc.spi.Row;
import org.linlinjava.announce.goods.domain.entities.AnnounceGoodsEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.function.BiFunction;


@Service
public class GoodsEntityRowMapper implements BiFunction<Row, String, AnnounceGoodsEntity> {


    private final ColumnConverter converter;

    public GoodsEntityRowMapper(ColumnConverter converter) {
        this.converter = converter;
    }


    @Override
    public AnnounceGoodsEntity apply(Row row, String s) {
        AnnounceGoodsEntity entity = new AnnounceGoodsEntity();

        entity.setId(converter.fromRow(row, "id", Integer.class));
        entity.setGoodsSn(converter.fromRow(row, "goods_sn", String.class));
        entity.setCategoryId(converter.fromRow(row, "category_id", Integer.class));
        entity.setBrandId(converter.fromRow(row, "brand_id", Integer.class));
        entity.setName(converter.fromRow(row, "name", String.class));
        entity.setGallery(converter.fromRow(row, "gallery", String.class));
        entity.setKeywords(converter.fromRow(row, "keywords", String.class));
        entity.setBrief(converter.fromRow(row, "brief", String.class));
        entity.setDetail(converter.fromRow(row, "detail", String.class));
        entity.setIsOnSale(converter.fromRow(row, "is_on_sale", Boolean.class));
        entity.setSortOrder(converter.fromRow(row, "sort_order", Short.class));
        entity.setPicUrl(converter.fromRow(row, "pic_url", String.class));
        entity.setShareUrl(converter.fromRow(row, "share_url", String.class));
        entity.setIsNew(converter.fromRow(row, "is_new", Boolean.class));

        entity.setAddTime(converter.fromRow(row, "add_time", LocalDateTime.class));
        entity.setUpdateTime(converter.fromRow(row, "update_time", LocalDateTime.class));
        entity.setDeleted(converter.fromRow(row, "deleted", Boolean.class));
        return null;
    }
}
