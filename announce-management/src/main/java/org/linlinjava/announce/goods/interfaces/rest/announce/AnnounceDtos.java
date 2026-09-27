package org.linlinjava.announce.goods.interfaces.rest.announce;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * JSON shapes of the announce API. A request carries the {@code kind} discriminator and exactly one of
 * {@code goods}, {@code job}, {@code event}; a response echoes the same layout. Requests are translated to domain
 * values (which validate themselves); responses are flat views of the aggregate state or of the read model.
 */
public final class AnnounceDtos {
    private AnnounceDtos() {}

    public record LocationDto(Integer regionId, String city, String postalCode, BigDecimal latitude, BigDecimal longitude) {
        static LocationDto of(Location l) {
            return l == null ? null : new LocationDto(l.regionId(), l.city(), l.postalCode(), l.latitude(), l.longitude());
        }
        Location toLocation() {
            return new Location(regionId, city, postalCode, latitude, longitude);
        }
    }

    public record AttributeDto(String name, String value) {}

    public record SpecificationDto(String name, String value, String pictureUrl) {}

    public record VariantDto(List<String> specifications, BigDecimal price, Integer stock, String pictureUrl) {}

    public record GoodsDto(BigDecimal price, Condition condition, SaleType saleType, Integer brandId, String unit,
                           List<VariantDto> variants, List<AttributeDto> attributes, List<SpecificationDto> specifications) {
        GoodsDetails toDetails() {
            return new GoodsDetails(price == null ? null : Money.of(price), condition, saleType,
                    brandId == null ? null : new BrandId(brandId), unit,
                    variants == null ? null : variants.stream().map(v -> new Variant(v.specifications(),
                            v.price() == null ? null : Money.of(v.price()), v.stock() == null ? 0 : v.stock(), v.pictureUrl())).toList(),
                    attributes == null ? null : attributes.stream().map(a -> new Attribute(a.name(), a.value())).toList(),
                    specifications == null ? null : specifications.stream().map(s -> new Specification(s.name(), s.value(), s.pictureUrl())).toList());
        }
        static GoodsDto of(GoodsDetails g) {
            return new GoodsDto(g.price().amount(), g.condition(), g.saleType(), g.brandId() == null ? null : g.brandId().value(), g.unit(),
                    g.variants().stream().map(v -> new VariantDto(v.specifications(), v.price().amount(), v.stock(), v.pictureUrl())).toList(),
                    g.attributes().stream().map(a -> new AttributeDto(a.name(), a.value())).toList(),
                    g.specifications().stream().map(s -> new SpecificationDto(s.name(), s.value(), s.pictureUrl())).toList());
        }
    }

    public record JobDto(String employer, ContractType contractType, BigDecimal salaryMin, BigDecimal salaryMax, String currency,
                         LocalDateTime applicationDeadline, String howToApply, SaleType saleType) {
        JobDetails toDetails() {
            SalaryRange range = salaryMin == null && salaryMax == null ? null : new SalaryRange(salaryMin, salaryMax, currency);
            return new JobDetails(employer, contractType, range, applicationDeadline, howToApply, saleType);
        }
        static JobDto of(JobDetails j) {
            SalaryRange r = j.salaryRange();
            return new JobDto(j.employer(), j.contractType(), r == null ? null : r.min(), r == null ? null : r.max(),
                    r == null ? null : r.currency(), j.applicationDeadline(), j.howToApply(), j.saleType());
        }
    }

    public record EventDto(LocalDateTime startsAt, LocalDateTime endsAt, String venue, String organizer, Boolean ticketed,
                           BigDecimal ticketPrice, Integer capacity, SaleType saleType) {
        EventDetails toDetails() {
            return new EventDetails(startsAt, endsAt, venue, organizer, Boolean.TRUE.equals(ticketed),
                    ticketPrice == null ? null : Money.of(ticketPrice), capacity, saleType);
        }
        static EventDto of(EventDetails e) {
            return new EventDto(e.startsAt(), e.endsAt(), e.venue(), e.organizer(), e.ticketed(),
                    e.ticketPrice() == null ? null : e.ticketPrice().amount(), e.capacity(), e.saleType());
        }
    }

    public record AnnounceRequest(Kind kind, String title, String brief, String description, List<String> keywords, List<String> gallery,
                                  Integer categoryId, LocationDto location, GoodsDto goods, JobDto job, EventDto event) {
        public AnnounceContent toContent() {
            return new AnnounceContent(title, brief, description, keywords, gallery,
                    categoryId == null ? null : new CategoryId(categoryId), location == null ? null : location.toLocation());
        }

        /** The details object must match {@code kind}; a missing kind defaults to GOODS for compatibility. */
        public AnnounceKind toDetails() {
            Kind k = kind == null ? Kind.GOODS : kind;
            return switch (k) {
                case GOODS -> require(goods, k).toDetails();
                case JOB -> require(job, k).toDetails();
                case EVENT -> require(event, k).toDetails();
            };
        }

        private static <T> T require(T details, Kind kind) {
            if (details == null) {
                throw new IllegalArgumentException("An announce of kind " + kind + " needs a '" + kind.name().toLowerCase() + "' object");
            }
            return details;
        }
    }

    public record PublishRequest(Integer validityDays) {}

    public record AnnounceResponse(String id, Kind kind, Integer sellerId, AnnounceStatus status, String title, String brief,
                                   String description, List<String> keywords, List<String> gallery, Integer categoryId,
                                   LocationDto location, GoodsDto goods, JobDto job, EventDto event,
                                   LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime publishedAt, LocalDateTime expiresAt) {
        public static AnnounceResponse of(Announce a) {
            AnnounceContent c = a.content();
            return new AnnounceResponse(a.id(), a.kind(), a.sellerId().value(), a.status(), c.title(), c.brief(), c.description(),
                    c.keywords(), c.gallery(), c.categoryId().value(), LocationDto.of(c.location()),
                    a.details() instanceof GoodsDetails g ? GoodsDto.of(g) : null,
                    a.details() instanceof JobDetails j ? JobDto.of(j) : null,
                    a.details() instanceof EventDetails e ? EventDto.of(e) : null,
                    a.createdAt(), a.updatedAt(), a.publishedAt(), a.expiresAt());
        }
    }

    public record AnnounceSummaryResponse(String id, Kind kind, Integer sellerId, String title, String brief, String pictureUrl,
                                          AnnounceStatus status, Integer categoryId, LocationDto location,
                                          LocalDateTime createdAt, LocalDateTime publishedAt, LocalDateTime expiresAt,
                                          BigDecimal price, SaleType saleType, Condition condition,
                                          String employer, ContractType contractType, LocalDateTime applicationDeadline,
                                          LocalDateTime startsAt, LocalDateTime endsAt, Boolean ticketed, BigDecimal ticketPrice,
                                          Integer moderatedBy, String moderationReason) {
        public static AnnounceSummaryResponse of(AnnounceSummary s) {
            AnnounceSummary.KindFacts f = s.kindFacts();
            AnnounceSummary.Moderation m = s.moderation();
            return new AnnounceSummaryResponse(s.id(), s.kind(), s.sellerId().value(), s.title(), s.brief(), s.pictureUrl(), s.status(),
                    s.categoryId().value(), LocationDto.of(s.location()), s.createdAt(), s.publishedAt(), s.expiresAt(),
                    f.price() == null ? null : f.price().amount(), f.saleType(), f.condition(),
                    f.employer(), f.contractType(), f.applicationDeadline(),
                    f.startsAt(), f.endsAt(), f.ticketed(), f.ticketPrice() == null ? null : f.ticketPrice().amount(),
                    m == null ? null : m.moderatedBy(), m == null ? null : m.reason());
        }
    }

    public record PageResponse<T>(List<T> items, int page, int size, long total) {}
}
