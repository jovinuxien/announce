package org.linlinjava.announce.goods.interfaces.rest.announce;

import org.linlinjava.announce.goods.api.AnnounceApiDtos.AnnounceResponse;
import org.linlinjava.announce.goods.api.AnnounceApiDtos.AnnounceSummaryResponse;
import org.linlinjava.announce.goods.api.AnnounceApiDtos.AttributeDto;
import org.linlinjava.announce.goods.api.AnnounceApiDtos.EventDto;
import org.linlinjava.announce.goods.api.AnnounceApiDtos.GoodsDto;
import org.linlinjava.announce.goods.api.AnnounceApiDtos.JobDto;
import org.linlinjava.announce.goods.api.AnnounceApiDtos.LocationDto;
import org.linlinjava.announce.goods.api.AnnounceApiDtos.SpecificationDto;
import org.linlinjava.announce.goods.api.AnnounceApiDtos.VariantDto;
import org.linlinjava.announce.goods.api.AnnounceKindV1;
import org.linlinjava.announce.goods.api.AnnounceStatusV1;
import org.linlinjava.announce.goods.api.ConditionV1;
import org.linlinjava.announce.goods.api.ContractTypeV1;
import org.linlinjava.announce.goods.api.SaleTypeV1;
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
import org.linlinjava.announce.goods.domain.announce.Specification;
import org.linlinjava.announce.goods.domain.announce.Variant;
import org.linlinjava.announce.goods.domain.brand.BrandId;
import org.linlinjava.announce.goods.domain.category.CategoryId;
import org.linlinjava.announce.goods.interfaces.rest.announce.AnnounceDtos.AnnounceRequest;

/**
 * Translation between the domain and the records of {@code announce-management-api}: requests become domain
 * values (which validate themselves), the aggregate and the read model become the response records the
 * {@link org.linlinjava.announce.goods.api.AnnounceApi descriptor} promises. The api enums mirror the domain enums
 * name for name, so the enum conversions are by name in both directions.
 */
public final class AnnounceApiMapper {
    private AnnounceApiMapper() {}

    // ---- enums, by name -------------------------------------------------------------------------------------

    private static <E extends Enum<E>> E as(Enum<?> value, Class<E> type) {
        return value == null ? null : Enum.valueOf(type, value.name());
    }

    public static Kind toDomain(AnnounceKindV1 kind) { return as(kind, Kind.class); }
    public static AnnounceStatus toDomain(AnnounceStatusV1 status) { return as(status, AnnounceStatus.class); }
    public static SaleType toDomain(SaleTypeV1 saleType) { return as(saleType, SaleType.class); }
    public static Condition toDomain(ConditionV1 condition) { return as(condition, Condition.class); }
    public static ContractType toDomain(ContractTypeV1 contractType) { return as(contractType, ContractType.class); }

    public static AnnounceKindV1 toApi(Kind kind) { return as(kind, AnnounceKindV1.class); }
    public static AnnounceStatusV1 toApi(AnnounceStatus status) { return as(status, AnnounceStatusV1.class); }
    public static SaleTypeV1 toApi(SaleType saleType) { return as(saleType, SaleTypeV1.class); }
    public static ConditionV1 toApi(Condition condition) { return as(condition, ConditionV1.class); }
    public static ContractTypeV1 toApi(ContractType contractType) { return as(contractType, ContractTypeV1.class); }

    // ---- request -> domain ---------------------------------------------------------------------------------

    public static AnnounceContent toContent(AnnounceRequest r) {
        return new AnnounceContent(r.title(), r.brief(), r.description(), r.keywords(), r.gallery(),
                r.categoryId() == null ? null : new CategoryId(r.categoryId()), toLocation(r.location()));
    }

    /** The details object must match {@code kind}; a missing kind defaults to GOODS for compatibility. */
    public static AnnounceKind toDetails(AnnounceRequest r) {
        Kind k = r.kind() == null ? Kind.GOODS : r.kind();
        return switch (k) {
            case GOODS -> toDetails(require(r.goods(), k));
            case JOB -> toDetails(require(r.job(), k));
            case EVENT -> toDetails(require(r.event(), k));
        };
    }

    private static <T> T require(T details, Kind kind) {
        if (details == null) {
            throw new IllegalArgumentException("An announce of kind " + kind + " needs a '" + kind.name().toLowerCase() + "' object");
        }
        return details;
    }

    static Location toLocation(LocationDto l) {
        return l == null ? null : new Location(l.regionId(), l.city(), l.postalCode(), l.latitude(), l.longitude());
    }

    static GoodsDetails toDetails(GoodsDto g) {
        return new GoodsDetails(g.price() == null ? null : Money.of(g.price()), toDomain(g.condition()), toDomain(g.saleType()),
                g.brandId() == null ? null : new BrandId(g.brandId()), g.unit(),
                g.variants() == null ? null : g.variants().stream().map(v -> new Variant(v.specifications(),
                        v.price() == null ? null : Money.of(v.price()), v.stock() == null ? 0 : v.stock(), v.pictureUrl())).toList(),
                g.attributes() == null ? null : g.attributes().stream().map(a -> new Attribute(a.name(), a.value())).toList(),
                g.specifications() == null ? null : g.specifications().stream().map(s -> new Specification(s.name(), s.value(), s.pictureUrl())).toList(),
                g.auctionStartsAt(), g.auctionEndsAt());
    }

    static JobDetails toDetails(JobDto j) {
        SalaryRange range = j.salaryMin() == null && j.salaryMax() == null ? null : new SalaryRange(j.salaryMin(), j.salaryMax(), j.currency());
        return new JobDetails(j.employer(), toDomain(j.contractType()), range, j.applicationDeadline(), j.howToApply(), toDomain(j.saleType()));
    }

    static EventDetails toDetails(EventDto e) {
        return new EventDetails(e.startsAt(), e.endsAt(), e.venue(), e.organizer(), Boolean.TRUE.equals(e.ticketed()),
                e.ticketPrice() == null ? null : Money.of(e.ticketPrice()), e.capacity(), toDomain(e.saleType()));
    }

    // ---- domain -> response ----------------------------------------------------------------------------------

    public static AnnounceResponse response(Announce a) {
        AnnounceContent c = a.content();
        return new AnnounceResponse(a.id(), toApi(a.kind()), a.sellerId().value(), toApi(a.status()), c.title(), c.brief(), c.description(),
                c.keywords(), c.gallery(), c.categoryId().value(), location(c.location()),
                a.details() instanceof GoodsDetails g ? goods(g) : null,
                a.details() instanceof JobDetails j ? job(j) : null,
                a.details() instanceof EventDetails e ? event(e) : null,
                a.createdAt(), a.updatedAt(), a.publishedAt(), a.expiresAt());
    }

    public static AnnounceSummaryResponse summary(AnnounceSummary s) {
        AnnounceSummary.KindFacts f = s.kindFacts();
        AnnounceSummary.Moderation m = s.moderation();
        return new AnnounceSummaryResponse(s.id(), toApi(s.kind()), s.sellerId().value(), s.title(), s.brief(), s.pictureUrl(), toApi(s.status()),
                s.categoryId().value(), location(s.location()), s.createdAt(), s.publishedAt(), s.expiresAt(),
                f.price() == null ? null : f.price().amount(), toApi(f.saleType()), toApi(f.condition()),
                f.employer(), toApi(f.contractType()), f.applicationDeadline(),
                f.startsAt(), f.endsAt(), f.ticketed(), f.ticketPrice() == null ? null : f.ticketPrice().amount(),
                m == null ? null : m.moderatedBy(), m == null ? null : m.reason(), f.auctionEndsAt());
    }

    static LocationDto location(Location l) {
        return l == null ? null : new LocationDto(l.regionId(), l.city(), l.postalCode(), l.latitude(), l.longitude());
    }

    static GoodsDto goods(GoodsDetails g) {
        return new GoodsDto(g.price().amount(), toApi(g.condition()), toApi(g.saleType()), g.brandId() == null ? null : g.brandId().value(), g.unit(),
                g.variants().stream().map(v -> new VariantDto(v.specifications(), v.price().amount(), v.stock(), v.pictureUrl())).toList(),
                g.attributes().stream().map(a -> new AttributeDto(a.name(), a.value())).toList(),
                g.specifications().stream().map(s -> new SpecificationDto(s.name(), s.value(), s.pictureUrl())).toList(),
                g.auctionStartsAt(), g.auctionEndsAt());
    }

    static JobDto job(JobDetails j) {
        SalaryRange r = j.salaryRange();
        return new JobDto(j.employer(), toApi(j.contractType()), r == null ? null : r.min(), r == null ? null : r.max(),
                r == null ? null : r.currency(), j.applicationDeadline(), j.howToApply(), toApi(j.saleType()));
    }

    static EventDto event(EventDetails e) {
        return new EventDto(e.startsAt(), e.endsAt(), e.venue(), e.organizer(), e.ticketed(),
                e.ticketPrice() == null ? null : e.ticketPrice().amount(), e.capacity(), toApi(e.saleType()));
    }
}
