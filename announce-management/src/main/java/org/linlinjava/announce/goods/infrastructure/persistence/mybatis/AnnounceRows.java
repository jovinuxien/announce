package org.linlinjava.announce.goods.infrastructure.persistence.mybatis;

import org.linlinjava.announce.db.domain.AnnounceBase;
import org.linlinjava.announce.db.domain.AnnounceEventDetail;
import org.linlinjava.announce.db.domain.AnnounceGoodsDetail;
import org.linlinjava.announce.db.domain.AnnounceJobDetail;
import org.linlinjava.announce.db.domain.AnnounceSummaryRow;
import org.linlinjava.announce.db.domain.LitemallGoodsAttribute;
import org.linlinjava.announce.db.domain.LitemallGoodsProduct;
import org.linlinjava.announce.db.domain.LitemallGoodsSpecification;
import org.linlinjava.announce.goods.domain.announce.Announce;
import org.linlinjava.announce.goods.domain.announce.AnnounceContent;
import org.linlinjava.announce.goods.domain.announce.AnnounceKind;
import org.linlinjava.announce.goods.domain.announce.AnnounceStatus;
import org.linlinjava.announce.goods.domain.announce.AnnounceSummary;
import org.linlinjava.announce.goods.domain.announce.Attribute;
import org.linlinjava.announce.goods.domain.announce.Condition;
import org.linlinjava.announce.goods.domain.announce.ContractType;
import org.linlinjava.announce.goods.domain.announce.EventDetails;
import org.linlinjava.announce.goods.domain.announce.GoodsDetails;
import org.linlinjava.announce.goods.domain.announce.JobDetails;
import org.linlinjava.announce.goods.domain.announce.Kind;
import org.linlinjava.announce.goods.domain.announce.Location;
import org.linlinjava.announce.goods.domain.announce.Money;
import org.linlinjava.announce.goods.domain.announce.SalaryRange;
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

/**
 * Translation between the read-model rows (generated db-reactive classes for {@code announce}, the detail tables and
 * the goods children) and the domain records. Used by the projection (domain to rows) and the read repository
 * (rows to domain).
 */
public final class AnnounceRows {
    private AnnounceRows() {}

    // ------------------------------------------------------------------ domain -> rows

    public static AnnounceBase toBaseRow(Announce a, String lastEventId) {
        AnnounceBase row = new AnnounceBase();
        row.setId(a.id());
        row.setKind(a.kind().name());
        row.setSellerId(a.sellerId().value());
        row.setStatus(a.status().name());
        row.setPublishedAt(a.publishedAt());
        row.setExpiresAt(a.expiresAt());
        row.setAddTime(a.createdAt());
        row.setUpdateTime(a.updatedAt());
        row.setDeleted(false);
        row.setLastEventId(lastEventId);
        fillContent(row, a.content());
        return row;
    }

    public static void fillContent(AnnounceBase row, AnnounceContent c) {
        row.setCategoryId(c.categoryId().value());
        row.setTitle(c.title());
        row.setBrief(c.brief());
        row.setDetail(c.description());
        row.setGallery(c.gallery().toArray(String[]::new));
        row.setKeywords(String.join(",", c.keywords()));
        Location l = c.location();
        row.setRegionId(l == null ? null : l.regionId());
        row.setCity(l == null ? null : l.city());
        row.setPostalCode(l == null ? null : l.postalCode());
        row.setLatitude(l == null ? null : l.latitude());
        row.setLongitude(l == null ? null : l.longitude());
    }

    public static AnnounceGoodsDetail toGoodsRow(String id, GoodsDetails g) {
        AnnounceGoodsDetail row = new AnnounceGoodsDetail();
        row.setAnnounceId(id);
        row.setPrice(g.price().amount());
        row.setCondition(g.condition() == null ? null : g.condition().name());
        row.setSaleType(g.saleType().name());
        row.setBrandId(g.brandId() == null ? null : g.brandId().value());
        row.setUnit(g.unit());
        return row;
    }

    public static AnnounceJobDetail toJobRow(String id, JobDetails j) {
        AnnounceJobDetail row = new AnnounceJobDetail();
        row.setAnnounceId(id);
        row.setEmployer(j.employer());
        row.setContractType(j.contractType().name());
        SalaryRange s = j.salaryRange();
        row.setSalaryMin(s == null ? null : s.min());
        row.setSalaryMax(s == null ? null : s.max());
        row.setCurrency(s == null ? null : s.currency());
        row.setApplicationDeadline(j.applicationDeadline());
        row.setApplyUrlOrEmail(j.howToApply());
        return row;
    }

    public static AnnounceEventDetail toEventRow(String id, EventDetails e) {
        AnnounceEventDetail row = new AnnounceEventDetail();
        row.setAnnounceId(id);
        row.setStartsAt(e.startsAt());
        row.setEndsAt(e.endsAt());
        row.setVenue(e.venue());
        row.setOrganizer(e.organizer());
        row.setTicketed(e.ticketed());
        row.setTicketPrice(e.ticketPrice() == null ? null : e.ticketPrice().amount());
        row.setCapacity(e.capacity());
        return row;
    }

    public static LitemallGoodsProduct toProductRow(String id, Variant v, LocalDateTime at) {
        LitemallGoodsProduct row = new LitemallGoodsProduct();
        row.setAnnounceId(id);
        row.setSpecifications(v.specifications().toArray(String[]::new));
        row.setPrice(v.price().amount());
        row.setNumber(v.stock());
        row.setUrl(v.pictureUrl());
        row.setAddTime(at);
        row.setUpdateTime(at);
        row.setDeleted(false);
        return row;
    }

    public static LitemallGoodsAttribute toAttributeRow(String id, Attribute a, LocalDateTime at) {
        LitemallGoodsAttribute row = new LitemallGoodsAttribute();
        row.setAnnounceId(id);
        row.setAttribute(a.name());
        row.setValue(a.value());
        row.setAddTime(at);
        row.setUpdateTime(at);
        row.setDeleted(false);
        return row;
    }

    public static LitemallGoodsSpecification toSpecificationRow(String id, Specification s, LocalDateTime at) {
        LitemallGoodsSpecification row = new LitemallGoodsSpecification();
        row.setAnnounceId(id);
        row.setSpecification(s.name());
        row.setValue(s.value());
        row.setPicUrl(s.pictureUrl());
        row.setAddTime(at);
        row.setUpdateTime(at);
        row.setDeleted(false);
        return row;
    }

    // ------------------------------------------------------------------ rows -> domain

    public static AnnounceContent toContent(AnnounceBase g) {
        return new AnnounceContent(g.getTitle(), g.getBrief(), g.getDetail(),
                g.getKeywords() == null || g.getKeywords().isBlank() ? List.of()
                        : Arrays.stream(g.getKeywords().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList(),
                g.getGallery() == null ? List.of() : Arrays.asList(g.getGallery()),
                new CategoryId(g.getCategoryId()),
                toLocation(g.getRegionId(), g.getCity(), g.getPostalCode(), g.getLatitude(), g.getLongitude()));
    }

    public static Announce toAnnounce(AnnounceBase g, AnnounceKind details) {
        return new Announce(g.getId(), new SellerId(g.getSellerId()), toContent(g), details,
                g.getStatus() == null ? AnnounceStatus.DRAFT : AnnounceStatus.valueOf(g.getStatus()),
                g.getPublishedAt(), g.getExpiresAt(), g.getAddTime(), g.getUpdateTime());
    }

    public static GoodsDetails toGoods(AnnounceGoodsDetail d, List<LitemallGoodsProduct> products,
                                       List<LitemallGoodsAttribute> attributes, List<LitemallGoodsSpecification> specifications) {
        return new GoodsDetails(money(d == null ? null : d.getPrice()),
                d == null || d.getCondition() == null ? null : Condition.valueOf(d.getCondition()),
                d == null || d.getSaleType() == null ? SaleType.FIXED_PRICE : SaleType.valueOf(d.getSaleType()),
                d == null || d.getBrandId() == null || d.getBrandId() == 0 ? null : new BrandId(d.getBrandId()),
                d == null ? null : d.getUnit(),
                products.stream().map(p -> new Variant(p.getSpecifications() == null ? List.of() : Arrays.asList(p.getSpecifications()),
                        money(p.getPrice()), p.getNumber() == null ? 0 : p.getNumber(), p.getUrl())).toList(),
                attributes.stream().map(a -> new Attribute(a.getAttribute(), a.getValue())).toList(),
                specifications.stream().map(s -> new Specification(s.getSpecification(), s.getValue(), s.getPicUrl())).toList());
    }

    public static JobDetails toJob(AnnounceJobDetail j) {
        SalaryRange range = j.getSalaryMin() == null && j.getSalaryMax() == null ? null
                : new SalaryRange(j.getSalaryMin(), j.getSalaryMax(), j.getCurrency());
        return JobDetails.of(j.getEmployer(), j.getContractType() == null ? null : ContractType.valueOf(j.getContractType()),
                range, j.getApplicationDeadline(), j.getApplyUrlOrEmail());
    }

    public static EventDetails toEvent(AnnounceEventDetail e) {
        boolean ticketed = Boolean.TRUE.equals(e.getTicketed());
        return new EventDetails(e.getStartsAt(), e.getEndsAt(), e.getVenue(), e.getOrganizer(), ticketed,
                ticketed && e.getTicketPrice() != null ? Money.of(e.getTicketPrice()) : null, e.getCapacity(), null);
    }

    public static AnnounceSummary toSummary(AnnounceSummaryRow r) {
        Kind kind = Kind.valueOf(r.getKind());
        AnnounceSummary.KindFacts facts = switch (kind) {
            case GOODS -> new AnnounceSummary.KindFacts(money(r.getPrice()),
                    r.getSaleType() == null ? SaleType.FIXED_PRICE : SaleType.valueOf(r.getSaleType()),
                    r.getCondition() == null ? null : Condition.valueOf(r.getCondition()), null, null, null, null, null, null, null);
            case JOB -> new AnnounceSummary.KindFacts(null, null, null, r.getEmployer(),
                    r.getContractType() == null ? null : ContractType.valueOf(r.getContractType()), r.getApplicationDeadline(),
                    null, null, null, null);
            case EVENT -> new AnnounceSummary.KindFacts(null, null, null, null, null, null, r.getStartsAt(), r.getEndsAt(),
                    Boolean.TRUE.equals(r.getTicketed()), r.getTicketPrice() == null ? null : Money.of(r.getTicketPrice()));
        };
        return new AnnounceSummary(r.getId(), kind, new SellerId(r.getSellerId()), r.getTitle(), r.getBrief(),
                r.getGallery() == null || r.getGallery().length == 0 ? "" : r.getGallery()[0],
                r.getStatus() == null ? AnnounceStatus.DRAFT : AnnounceStatus.valueOf(r.getStatus()),
                new CategoryId(r.getCategoryId()),
                toLocation(r.getRegionId(), r.getCity(), r.getPostalCode(), r.getLatitude(), r.getLongitude()),
                r.getAddTime(), r.getPublishedAt(), r.getExpiresAt(), facts,
                r.getModeratedBy() == null ? null : new AnnounceSummary.Moderation(r.getModeratedBy(), r.getModerationReason()));
    }

    static Location toLocation(Integer regionId, String city, String postalCode, BigDecimal latitude, BigDecimal longitude) {
        if (regionId == null && (city == null || city.isBlank()) && (postalCode == null || postalCode.isBlank())) {
            return null;
        }
        return new Location(regionId, city, postalCode, latitude, longitude);
    }

    private static Money money(BigDecimal amount) {
        return Money.of(amount == null ? BigDecimal.ZERO : amount);
    }
}
