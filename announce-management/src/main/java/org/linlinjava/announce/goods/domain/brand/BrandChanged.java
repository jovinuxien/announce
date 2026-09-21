package org.linlinjava.announce.goods.domain.brand;

import org.linlinjava.announce.goods.domain.model.events.LitemallDomainEvent;

public final class BrandChanged extends LitemallDomainEvent {
    public enum Kind { CREATED, RENAMED, HIDDEN }

    private final BrandId brandId;
    private final Kind kind;

    public BrandChanged(BrandId brandId, Kind kind) {
        this.brandId = brandId;
        this.kind = kind;
    }

    public BrandId brandId() { return brandId; }
    public Kind kind() { return kind; }

    @Override
    public String toString() {
        return "BrandChanged[" + brandId.value() + ", " + kind + "]";
    }
}
