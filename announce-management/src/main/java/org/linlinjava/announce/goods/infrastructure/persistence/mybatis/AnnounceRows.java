package org.linlinjava.announce.goods.infrastructure.persistence.mybatis;

import org.linlinjava.announce.db.domain.LitemallGoods;
import org.linlinjava.announce.db.domain.LitemallGoodsAttribute;
import org.linlinjava.announce.db.domain.LitemallGoodsProduct;
import org.linlinjava.announce.db.domain.LitemallGoodsSpecification;
import org.linlinjava.announce.goods.domain.announce.Announce;
import org.linlinjava.announce.goods.domain.announce.AnnounceDetails;
import org.linlinjava.announce.goods.domain.announce.AnnounceId;
import org.linlinjava.announce.goods.domain.announce.AnnounceStatus;
import org.linlinjava.announce.goods.domain.announce.AnnounceSummary;
import org.linlinjava.announce.goods.domain.announce.Attribute;
import org.linlinjava.announce.goods.domain.announce.Location;
import org.linlinjava.announce.goods.domain.announce.Money;
import org.linlinjava.announce.goods.domain.announce.SaleType;
import org.linlinjava.announce.goods.domain.announce.SellerId;
import org.linlinjava.announce.goods.domain.announce.Specification;
import org.linlinjava.announce.goods.domain.announce.Variant;
import org.linlinjava.announce.goods.domain.brand.BrandId;
import org.linlinjava.announce.goods.domain.category.CategoryId;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** Translation between the announce_goods* rows (generated MyBatis classes) and the Announce aggregate. */
final class AnnounceRows {
    private AnnounceRows() {}

    static Announce toAnnounce(LitemallGoods g, List<LitemallGoodsProduct> productRows,
                               List<LitemallGoodsAttribute> attributeRows, List<LitemallGoodsSpecification> specificationRows) {
        AnnounceDetails details = new AnnounceDetails(
                g.getName(), g.getBrief(), g.getDetail(),
                g.getKeywords() == null || g.getKeywords().isBlank() ? List.of() : Arrays.stream(g.getKeywords().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList(),
                g.getGallery() == null ? List.of() : Arrays.asList(g.getGallery()),
                g.getUnit(),
                new CategoryId(g.getCategoryId()),
                g.getBrandId() == null || g.getBrandId() == 0 ? null : new BrandId(g.getBrandId()),
                g.getSaleType() == null ? SaleType.FIXED_PRICE : SaleType.valueOf(g.getSaleType()),
                Money.of(g.getRetailPrice() == null ? BigDecimal.ZERO : g.getRetailPrice()),
                toLocation(g),
                attributeRows.stream().map(a -> new Attribute(a.getAttribute(), a.getValue())).toList(),
                specificationRows.stream().map(s -> new Specification(s.getSpecification(), s.getValue(), s.getPicUrl())).toList());
        List<Variant> variants = productRows.stream()
                .map(p -> Variant.rehydrate(p.getId(),
                        p.getSpecifications() == null ? List.of() : Arrays.asList(p.getSpecifications()),
                        Money.of(p.getPrice() == null ? BigDecimal.ZERO : p.getPrice()),
                        p.getNumber() == null ? 0 : p.getNumber(),
                        p.getUrl()))
                .toList();
        AnnounceStatus status = g.getStatus() == null ? (Boolean.TRUE.equals(g.getIsOnSale()) ? AnnounceStatus.PUBLISHED : AnnounceStatus.DRAFT) : AnnounceStatus.valueOf(g.getStatus());
        // publishedAt is not stored; the aggregate only needs expiresAt to reason about expiry
        return Announce.rehydrate(new AnnounceId(g.getId()), new SellerId(g.getSellerId()), g.getGoodsSn(), details, status,
                null, g.getExpireTime(), variants, g.getAddTime(), g.getUpdateTime());
    }

    static AnnounceSummary toSummary(LitemallGoods g) {
        return new AnnounceSummary(new AnnounceId(g.getId()), new SellerId(g.getSellerId()), g.getName(), g.getBrief(), g.getPicUrl(),
                Money.of(g.getRetailPrice() == null ? BigDecimal.ZERO : g.getRetailPrice()),
                g.getSaleType() == null ? SaleType.FIXED_PRICE : SaleType.valueOf(g.getSaleType()),
                g.getStatus() == null ? AnnounceStatus.PUBLISHED : AnnounceStatus.valueOf(g.getStatus()),
                new CategoryId(g.getCategoryId()), toLocation(g), g.getAddTime(), g.getExpireTime());
    }

    static Location toLocation(LitemallGoods g) {
        if (g.getRegionId() == null && g.getCity() == null && g.getPostalCode() == null) {
            return null;
        }
        return new Location(g.getRegionId(), g.getCity(), g.getPostalCode(), g.getLatitude(), g.getLongitude());
    }

    /** Full row for an insert (marketing flags off, default sort order). */
    static LitemallGoods toNewRow(Announce a) {
        LitemallGoods g = toPatchRow(a);
        g.setGoodsSn(a.reference());
        g.setShareUrl("");
        g.setIsNew(false);
        g.setIsHot(false);
        g.setSortOrder((short) 100);
        g.setAddTime(a.createdAt());
        g.setDeleted(false);
        g.setSellerId(a.sellerId().value());
        return g;
    }

    /** Only the columns the aggregate owns; marketing flags and audit columns set elsewhere stay untouched. */
    static LitemallGoods toPatchRow(Announce a) {
        AnnounceDetails d = a.details();
        LitemallGoods g = new LitemallGoods();
        g.setId(a.id() == null ? null : a.id().value());
        g.setName(d.title());
        g.setBrief(d.brief());
        g.setDetail(d.description());
        g.setKeywords(String.join(",", d.keywords()));
        g.setGallery(d.gallery().toArray(new String[0]));
        g.setPicUrl(d.mainPicture());
        g.setUnit(d.unit());
        g.setCategoryId(d.categoryId().value());
        g.setBrandId(d.brandId() == null ? 0 : d.brandId().value());
        g.setSaleType(d.saleType().name());
        g.setCounterPrice(d.price().amount());
        g.setRetailPrice(d.price().amount());
        g.setStatus(a.status().name());
        g.setIsOnSale(a.status().isPublished());
        g.setExpireTime(a.expiresAt());
        Location l = d.location();
        g.setRegionId(l == null ? null : l.regionId());
        g.setCity(l == null ? null : l.city());
        g.setPostalCode(l == null ? null : l.postalCode());
        g.setLatitude(l == null ? null : l.latitude());
        g.setLongitude(l == null ? null : l.longitude());
        g.setUpdateTime(a.updatedAt());
        return g;
    }

    static LitemallGoodsProduct toNewProductRow(Integer goodsId, Variant v, LocalDateTime at) {
        LitemallGoodsProduct p = toProductPatch(v, at);
        p.setGoodsId(goodsId);
        p.setSpecifications(v.specifications().toArray(new String[0]));
        p.setAddTime(at);
        p.setDeleted(false);
        return p;
    }

    static LitemallGoodsProduct toProductPatch(Variant v, LocalDateTime at) {
        LitemallGoodsProduct p = new LitemallGoodsProduct();
        p.setId(v.id());
        p.setPrice(v.price().amount());
        p.setNumber(v.stock());
        p.setUrl(v.pictureUrl());
        p.setUpdateTime(at);
        return p;
    }

    static LitemallGoodsAttribute toAttributeRow(Integer goodsId, Attribute a, LocalDateTime at) {
        LitemallGoodsAttribute row = new LitemallGoodsAttribute();
        row.setGoodsId(goodsId);
        row.setAttribute(a.name());
        row.setValue(a.value());
        row.setAddTime(at);
        row.setUpdateTime(at);
        row.setDeleted(false);
        return row;
    }

    static LitemallGoodsSpecification toSpecificationRow(Integer goodsId, Specification s, LocalDateTime at) {
        LitemallGoodsSpecification row = new LitemallGoodsSpecification();
        row.setGoodsId(goodsId);
        row.setSpecification(s.name());
        row.setValue(s.value());
        row.setPicUrl(s.pictureUrl());
        row.setAddTime(at);
        row.setUpdateTime(at);
        row.setDeleted(false);
        return row;
    }
}
