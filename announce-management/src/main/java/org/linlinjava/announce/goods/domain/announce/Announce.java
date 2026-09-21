package org.linlinjava.announce.goods.domain.announce;

import org.linlinjava.announce.goods.domain.announce.events.AnnounceEvent;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceExpired;
import org.linlinjava.announce.goods.domain.announce.events.AnnouncePriceChanged;
import org.linlinjava.announce.goods.domain.announce.events.AnnouncePublished;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceSold;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceUpdated;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceWithdrawn;
import org.linlinjava.announce.goods.domain.announce.events.StockAdjusted;
import org.linlinjava.announce.goods.domain.brand.BrandId;
import org.linlinjava.announce.goods.domain.category.CategoryId;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate root of the announce catalog: an item a member puts up for sale, auction or giveaway.
 *
 * <p>Owns its {@link Variant}s, {@link Attribute}s and {@link Specification}s (one transaction), references
 * category, brand and seller by identity only, and records the domain events other contexts react to.
 * Stored in announce_goods and its three child tables.
 */
public final class Announce {

    /** Blocket keeps an ad for 60 days by default. */
    public static final Duration DEFAULT_VALIDITY = Duration.ofDays(60);
    public static final int MAX_VARIANTS = 20;

    private AnnounceId id;
    private final SellerId sellerId;
    private final String reference;
    private AnnounceDetails details;
    private AnnounceStatus status;
    private LocalDateTime publishedAt;
    private LocalDateTime expiresAt;
    private final List<Variant> variants;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private final List<AnnounceEvent> events = new ArrayList<>();

    private Announce(AnnounceId id, SellerId sellerId, String reference, AnnounceDetails details, AnnounceStatus status,
                     LocalDateTime publishedAt, LocalDateTime expiresAt, List<Variant> variants,
                     LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.sellerId = Objects.requireNonNull(sellerId, "sellerId");
        this.reference = Objects.requireNonNull(reference, "reference");
        this.details = Objects.requireNonNull(details, "details");
        this.status = Objects.requireNonNull(status, "status");
        this.publishedAt = publishedAt;
        this.expiresAt = expiresAt;
        this.variants = new ArrayList<>(variants);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** A member starts a new announce. It is not visible until {@link #publish}. */
    public static Announce draft(SellerId sellerId, AnnounceDetails details, LocalDateTime now) {
        String reference = "A" + now.toLocalDate().toString().replace("-", "") + "-" + Long.toString(System.nanoTime() % 1_000_000_000L, 36).toUpperCase();
        return new Announce(null, sellerId, reference, details, AnnounceStatus.DRAFT, null, null, List.of(), now, now);
    }

    /** Used by the repository when loading from the database. */
    public static Announce rehydrate(AnnounceId id, SellerId sellerId, String reference, AnnounceDetails details, AnnounceStatus status,
                                     LocalDateTime publishedAt, LocalDateTime expiresAt, List<Variant> variants,
                                     LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new Announce(Objects.requireNonNull(id, "id"), sellerId, reference, details, status, publishedAt, expiresAt, variants, createdAt, updatedAt);
    }

    // ------------------------------------------------------------------ behaviour

    /** Makes the announce visible for {@code validity}. Rules: complete details, a location, a price for fixed-price sales. */
    public void publish(Duration validity, LocalDateTime now) {
        if (!status.canBePublished()) {
            throw new AnnounceRuleViolation("An announce in status " + status + " cannot be published");
        }
        if (details.location() == null) {
            throw new AnnounceRuleViolation("An announce needs a location before it is published");
        }
        if (details.saleType() == SaleType.FIXED_PRICE && details.price().isZero() && variants.isEmpty()) {
            throw new AnnounceRuleViolation("A fixed-price announce needs a price");
        }
        if (validity == null || validity.isNegative() || validity.isZero()) {
            throw new AnnounceRuleViolation("Validity must be positive");
        }
        status = AnnounceStatus.PUBLISHED;
        publishedAt = now;
        expiresAt = now.plus(validity);
        touch(now);
        record(new AnnouncePublished(id, sellerId, details.categoryId(), details.saleType(), details.price(), expiresAt));
    }

    /** Changes the content. Allowed in every status except SOLD; a published announce stays published. */
    public void edit(AnnounceDetails newDetails, LocalDateTime now) {
        if (status == AnnounceStatus.SOLD) {
            throw new AnnounceRuleViolation("A sold announce cannot be edited");
        }
        Objects.requireNonNull(newDetails, "details");
        boolean priceChanged = !newDetails.price().equals(details.price());
        Money previous = details.price();
        details = newDetails;
        touch(now);
        record(new AnnounceUpdated(id, sellerId));
        if (priceChanged) {
            record(new AnnouncePriceChanged(id, sellerId, previous, newDetails.price()));
        }
    }

    public void changePrice(Money newPrice, LocalDateTime now) {
        if (status == AnnounceStatus.SOLD) {
            throw new AnnounceRuleViolation("A sold announce cannot be repriced");
        }
        if (details.saleType() == SaleType.GIVEAWAY) {
            throw new AnnounceRuleViolation("A giveaway has no price");
        }
        if (details.saleType() == SaleType.AUCTION) {
            throw new AnnounceRuleViolation("The price of an auction is set by the bids");
        }
        Money previous = details.price();
        details = new AnnounceDetails(details.title(), details.brief(), details.description(), details.keywords(), details.gallery(),
                details.unit(), details.categoryId(), details.brandId(), details.saleType(), newPrice, details.location(),
                details.attributes(), details.specifications());
        touch(now);
        record(new AnnouncePriceChanged(id, sellerId, previous, newPrice));
    }

    public void withdraw(LocalDateTime now) {
        if (status != AnnounceStatus.PUBLISHED) {
            throw new AnnounceRuleViolation("Only a published announce can be withdrawn (status " + status + ")");
        }
        status = AnnounceStatus.WITHDRAWN;
        touch(now);
        record(new AnnounceWithdrawn(id, sellerId));
    }

    public void markSold(LocalDateTime now) {
        if (status != AnnounceStatus.PUBLISHED) {
            throw new AnnounceRuleViolation("Only a published announce can be marked sold (status " + status + ")");
        }
        status = AnnounceStatus.SOLD;
        touch(now);
        record(new AnnounceSold(id, sellerId));
    }

    /** Called by the scheduler. Returns true when the announce just expired. */
    public boolean expireIfDue(LocalDateTime now) {
        if (status != AnnounceStatus.PUBLISHED || expiresAt == null || expiresAt.isAfter(now)) {
            return false;
        }
        status = AnnounceStatus.EXPIRED;
        touch(now);
        record(new AnnounceExpired(id, sellerId));
        return true;
    }

    public Variant addVariant(List<String> specifications, Money price, int stock, String pictureUrl, LocalDateTime now) {
        if (status == AnnounceStatus.SOLD) {
            throw new AnnounceRuleViolation("A sold announce cannot get new variants");
        }
        if (variants.size() >= MAX_VARIANTS) {
            throw new AnnounceRuleViolation("An announce has at most " + MAX_VARIANTS + " variants");
        }
        Variant variant = Variant.of(specifications, price, stock, pictureUrl);
        variants.add(variant);
        touch(now);
        record(new AnnounceUpdated(id, sellerId));
        return variant;
    }

    /** Moves stock of one variant up or down; never below zero. */
    public Variant adjustStock(Integer variantId, int delta, LocalDateTime now) {
        Variant variant = variants.stream()
                .filter(v -> Objects.equals(v.id(), variantId))
                .findFirst()
                .orElseThrow(() -> new AnnounceRuleViolation("Variant " + variantId + " does not belong to announce " + (id == null ? "?" : id.value())));
        variant.adjustStock(delta);
        touch(now);
        record(new StockAdjusted(id, sellerId, variantId, delta, variant.stock()));
        return variant;
    }

    // ------------------------------------------------------------------ persistence hooks

    /** Called by the repository once the row is inserted; events recorded before that get their id now. */
    public void assignId(AnnounceId newId) {
        if (this.id != null) {
            throw new IllegalStateException("Announce already has an id");
        }
        this.id = Objects.requireNonNull(newId, "id");
    }

    /** Hands out the events recorded since the last call and clears them. */
    public List<AnnounceEvent> pullEvents() {
        List<AnnounceEvent> pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }

    private void record(AnnounceEvent event) {
        events.add(event);
    }

    private void touch(LocalDateTime now) {
        updatedAt = now;
    }

    // ------------------------------------------------------------------ accessors

    public AnnounceId id() { return id; }
    public SellerId sellerId() { return sellerId; }
    public String reference() { return reference; }
    public AnnounceDetails details() { return details; }
    public AnnounceStatus status() { return status; }
    public LocalDateTime publishedAt() { return publishedAt; }
    public LocalDateTime expiresAt() { return expiresAt; }
    public List<Variant> variants() { return Collections.unmodifiableList(variants); }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }
    public boolean isOwnedBy(SellerId candidate) { return sellerId.equals(candidate); }
    public boolean isVisible() { return status.isPublished(); }
}
