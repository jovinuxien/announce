package org.linlinjava.announce.goods.interfaces.rest.announce;

import org.linlinjava.announce.goods.domain.announce.Announce;
import org.linlinjava.announce.goods.domain.announce.AnnounceDetails;
import org.linlinjava.announce.goods.domain.announce.AnnounceStatus;
import org.linlinjava.announce.goods.domain.announce.AnnounceSummary;
import org.linlinjava.announce.goods.domain.announce.Attribute;
import org.linlinjava.announce.goods.domain.announce.Location;
import org.linlinjava.announce.goods.domain.announce.Money;
import org.linlinjava.announce.goods.domain.announce.SaleType;
import org.linlinjava.announce.goods.domain.announce.Specification;
import org.linlinjava.announce.goods.domain.announce.Variant;
import org.linlinjava.announce.goods.domain.brand.BrandId;
import org.linlinjava.announce.goods.domain.category.CategoryId;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** JSON shapes of the announce API. Requests are translated to domain values; responses are flat views. */
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

    public record AnnounceRequest(String title, String brief, String description, List<String> keywords, List<String> gallery,
                                  String unit, Integer categoryId, Integer brandId, SaleType saleType, BigDecimal price,
                                  LocationDto location, List<AttributeDto> attributes, List<SpecificationDto> specifications) {
        public AnnounceDetails toDetails() {
            return new AnnounceDetails(title, brief, description, keywords, gallery, unit,
                    categoryId == null ? null : new CategoryId(categoryId),
                    brandId == null ? null : new BrandId(brandId),
                    saleType, price == null ? null : Money.of(price),
                    location == null ? null : location.toLocation(),
                    attributes == null ? null : attributes.stream().map(a -> new Attribute(a.name(), a.value())).toList(),
                    specifications == null ? null : specifications.stream().map(s -> new Specification(s.name(), s.value(), s.pictureUrl())).toList());
        }
    }

    public record PublishRequest(Integer validityDays) {}

    public record PriceRequest(BigDecimal price) {}

    public record VariantRequest(List<String> specifications, BigDecimal price, Integer stock, String pictureUrl) {}

    public record StockRequest(Integer delta) {}

    public record VariantResponse(Integer id, List<String> specifications, BigDecimal price, int stock, String pictureUrl) {
        static VariantResponse of(Variant v) {
            return new VariantResponse(v.id(), v.specifications(), v.price().amount(), v.stock(), v.pictureUrl());
        }
    }

    public record AnnounceResponse(Integer id, String reference, Integer sellerId, AnnounceStatus status, String title, String brief,
                                   String description, List<String> keywords, List<String> gallery, String unit, Integer categoryId,
                                   Integer brandId, SaleType saleType, BigDecimal price, LocationDto location,
                                   List<AttributeDto> attributes, List<SpecificationDto> specifications, List<VariantResponse> variants,
                                   LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime expiresAt) {
        public static AnnounceResponse of(Announce a) {
            AnnounceDetails d = a.details();
            return new AnnounceResponse(a.id() == null ? null : a.id().value(), a.reference(), a.sellerId().value(), a.status(),
                    d.title(), d.brief(), d.description(), d.keywords(), d.gallery(), d.unit(), d.categoryId().value(),
                    d.brandId() == null ? null : d.brandId().value(), d.saleType(), d.price().amount(), LocationDto.of(d.location()),
                    d.attributes().stream().map(x -> new AttributeDto(x.name(), x.value())).toList(),
                    d.specifications().stream().map(x -> new SpecificationDto(x.name(), x.value(), x.pictureUrl())).toList(),
                    a.variants().stream().map(VariantResponse::of).toList(),
                    a.createdAt(), a.updatedAt(), a.expiresAt());
        }
    }

    public record AnnounceSummaryResponse(Integer id, Integer sellerId, String title, String brief, String pictureUrl, BigDecimal price,
                                          SaleType saleType, AnnounceStatus status, Integer categoryId, LocationDto location,
                                          LocalDateTime createdAt, LocalDateTime expiresAt) {
        public static AnnounceSummaryResponse of(AnnounceSummary s) {
            return new AnnounceSummaryResponse(s.id().value(), s.sellerId().value(), s.title(), s.brief(), s.pictureUrl(), s.price().amount(),
                    s.saleType(), s.status(), s.categoryId().value(), LocationDto.of(s.location()), s.createdAt(), s.expiresAt());
        }
    }

    public record PageResponse<T>(List<T> items, int page, int size, long total) {}
}
