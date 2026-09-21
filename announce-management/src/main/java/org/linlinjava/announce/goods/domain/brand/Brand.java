package org.linlinjava.announce.goods.domain.brand;

import org.linlinjava.announce.goods.domain.announce.Money;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A brand or manufacturer members can attach to an announce (announce_brand). Optional reference data, own aggregate. */
public final class Brand {

    private BrandId id;
    private String name;
    private String description;
    private String pictureUrl;
    private int sortOrder;
    private Money floorPrice;
    private boolean hidden;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private final List<BrandChanged> events = new ArrayList<>();

    private Brand(BrandId id, String name, String description, String pictureUrl, int sortOrder, Money floorPrice, boolean hidden,
                  LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = validName(name);
        this.description = description == null ? "" : description;
        this.pictureUrl = pictureUrl == null ? "" : pictureUrl;
        this.sortOrder = sortOrder;
        this.floorPrice = floorPrice == null ? Money.zero() : floorPrice;
        this.hidden = hidden;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Brand create(String name, String description, String pictureUrl, int sortOrder, LocalDateTime now) {
        return new Brand(null, name, description, pictureUrl, sortOrder, Money.zero(), false, now, now);
    }

    public static Brand rehydrate(BrandId id, String name, String description, String pictureUrl, int sortOrder, Money floorPrice,
                                  boolean hidden, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new Brand(Objects.requireNonNull(id, "id"), name, description, pictureUrl, sortOrder, floorPrice, hidden, createdAt, updatedAt);
    }

    public void rename(String newName, String description, String pictureUrl, int sortOrder, LocalDateTime now) {
        this.name = validName(newName);
        this.description = description == null ? this.description : description;
        this.pictureUrl = pictureUrl == null ? this.pictureUrl : pictureUrl;
        this.sortOrder = sortOrder;
        updatedAt = now;
        if (id != null) events.add(new BrandChanged(id, BrandChanged.Kind.RENAMED));
    }

    public void hide(LocalDateTime now) {
        if (!hidden) {
            hidden = true;
            updatedAt = now;
            if (id != null) events.add(new BrandChanged(id, BrandChanged.Kind.HIDDEN));
        }
    }

    public void assignId(BrandId newId) {
        if (id != null) {
            throw new IllegalStateException("Brand already has an id");
        }
        id = Objects.requireNonNull(newId, "id");
        events.add(new BrandChanged(id, BrandChanged.Kind.CREATED));
    }

    public List<BrandChanged> pullEvents() {
        List<BrandChanged> pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }

    private static String validName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Brand name is required");
        }
        return name.trim();
    }

    public BrandId id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public String pictureUrl() { return pictureUrl; }
    public int sortOrder() { return sortOrder; }
    public Money floorPrice() { return floorPrice; }
    public boolean isHidden() { return hidden; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }
}
