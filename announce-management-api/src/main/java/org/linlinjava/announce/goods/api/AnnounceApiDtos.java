package org.linlinjava.announce.goods.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * JSON shapes the announce API answers with, as consumers see them: flat records without behaviour. A response
 * carries the {@code kind} discriminator and exactly one of {@code goods}, {@code job}, {@code event}. The provider
 * (announce-management) maps its aggregate and read model onto these records; a consumer deserialises them through
 * {@link AnnounceApi}. Changing a field is a contract change: add fields, never rename or retype them, and version
 * the record when a breaking change is unavoidable.
 */
public final class AnnounceApiDtos {
    private AnnounceApiDtos() {}

    public record LocationDto(Integer regionId, String city, String postalCode, BigDecimal latitude, BigDecimal longitude) {}

    public record AttributeDto(String name, String value) {}

    public record SpecificationDto(String name, String value, String pictureUrl) {}

    public record VariantDto(List<String> specifications, BigDecimal price, Integer stock, String pictureUrl) {}

    /** {@code auctionStartsAt} / {@code auctionEndsAt}: the auction schedule (AUCTION only; both set once published, optional on a draft). */
    public record GoodsDto(BigDecimal price, ConditionV1 condition, SaleTypeV1 saleType, Integer brandId, String unit,
                           List<VariantDto> variants, List<AttributeDto> attributes, List<SpecificationDto> specifications,
                           LocalDateTime auctionStartsAt, LocalDateTime auctionEndsAt) {}

    public record JobDto(String employer, ContractTypeV1 contractType, BigDecimal salaryMin, BigDecimal salaryMax, String currency,
                         LocalDateTime applicationDeadline, String howToApply, SaleTypeV1 saleType) {}

    public record EventDto(LocalDateTime startsAt, LocalDateTime endsAt, String venue, String organizer, Boolean ticketed,
                           BigDecimal ticketPrice, Integer capacity, SaleTypeV1 saleType) {}

    /** One announce in full: the state of the aggregate after a command, or the read model's row. */
    public record AnnounceResponse(String id, AnnounceKindV1 kind, Integer sellerId, AnnounceStatusV1 status, String title, String brief,
                                   String description, List<String> keywords, List<String> gallery, Integer categoryId,
                                   LocationDto location, GoodsDto goods, JobDto job, EventDto event,
                                   LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime publishedAt, LocalDateTime expiresAt) {}

    /** One row of a listing: the shared facts plus the kind-specific facts a result card shows. */
    public record AnnounceSummaryResponse(String id, AnnounceKindV1 kind, Integer sellerId, String title, String brief, String pictureUrl,
                                          AnnounceStatusV1 status, Integer categoryId, LocationDto location,
                                          LocalDateTime createdAt, LocalDateTime publishedAt, LocalDateTime expiresAt,
                                          BigDecimal price, SaleTypeV1 saleType, ConditionV1 condition,
                                          String employer, ContractTypeV1 contractType, LocalDateTime applicationDeadline,
                                          LocalDateTime startsAt, LocalDateTime endsAt, Boolean ticketed, BigDecimal ticketPrice,
                                          Integer moderatedBy, String moderationReason,
                                          LocalDateTime auctionEndsAt) {}

    public record PageResponse<T>(List<T> items, int page, int size, long total) {}
}
