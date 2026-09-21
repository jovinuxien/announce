package org.linlinjava.announce.goods.domain.announce;

import java.util.List;
import java.util.Objects;

/**
 * A sellable variant of the announce (announce_goods_product): a combination of specification values with its own
 * price, stock and picture. Child entity of {@link Announce}; only the aggregate root changes it.
 */
public final class Variant {

    private Integer id;
    private final List<String> specifications;
    private Money price;
    private int stock;
    private String pictureUrl;

    private Variant(Integer id, List<String> specifications, Money price, int stock, String pictureUrl) {
        this.id = id;
        this.specifications = List.copyOf(Objects.requireNonNull(specifications, "specifications"));
        this.price = Objects.requireNonNull(price, "price");
        if (stock < 0) {
            throw new IllegalArgumentException("Stock must not be negative");
        }
        this.stock = stock;
        this.pictureUrl = pictureUrl == null ? "" : pictureUrl;
    }

    public static Variant of(List<String> specifications, Money price, int stock, String pictureUrl) {
        return new Variant(null, specifications, price, stock, pictureUrl);
    }

    /** Used by the repository when loading from the database. */
    public static Variant rehydrate(Integer id, List<String> specifications, Money price, int stock, String pictureUrl) {
        return new Variant(Objects.requireNonNull(id, "id"), specifications, price, stock, pictureUrl);
    }

    void adjustStock(int delta) {
        if (stock + delta < 0) {
            throw new AnnounceRuleViolation("Stock of variant " + id + " cannot go below zero (has " + stock + ", asked " + delta + ")");
        }
        stock += delta;
    }

    void reprice(Money newPrice) {
        this.price = Objects.requireNonNull(newPrice, "price");
    }

    /** Called by the repository once the row is inserted. */
    public void assignId(Integer id) {
        if (this.id != null) {
            throw new IllegalStateException("Variant already has an id");
        }
        this.id = Objects.requireNonNull(id, "id");
    }

    public boolean isNew() {
        return id == null;
    }

    public boolean isSoldOut() {
        return stock == 0;
    }

    public Integer id() { return id; }
    public List<String> specifications() { return specifications; }
    public Money price() { return price; }
    public int stock() { return stock; }
    public String pictureUrl() { return pictureUrl; }
}
