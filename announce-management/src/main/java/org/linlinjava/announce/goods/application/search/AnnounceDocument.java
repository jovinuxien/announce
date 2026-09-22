package org.linlinjava.announce.goods.application.search;

import org.linlinjava.announce.goods.domain.announce.Announce;
import org.linlinjava.announce.goods.domain.announce.AnnounceContent;
import org.linlinjava.announce.goods.domain.announce.EventDetails;
import org.linlinjava.announce.goods.domain.announce.GoodsDetails;
import org.linlinjava.announce.goods.domain.announce.JobDetails;
import org.linlinjava.announce.goods.domain.announce.Location;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The search document of an announce, as the OCS indexer expects it: an id and flat fields. Field names follow
 * {@code docker/cqrs/application.indexer-service.yml}; {@code kind} is a facet, the kind-specific fields are only
 * present for that kind, goods attributes become dynamic {@code attribute_*} facets.
 */
public record AnnounceDocument(String id, Map<String, Object> data) {

    public static AnnounceDocument of(Announce a) {
        AnnounceContent c = a.content();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("announce_id", a.id());
        d.put("kind", a.kind().name());
        d.put("title", c.title());
        d.put("description", c.brief().isEmpty() ? c.description() : c.brief());
        d.put("status", a.status().name());
        d.put("category_ids", String.valueOf(c.categoryId().value()));
        d.put("seller_id", String.valueOf(a.sellerId().value()));
        d.put("image_url", c.mainPicture());
        d.put("created_epoch", epoch(a.createdAt()));
        d.put("expire_epoch", epoch(a.expiresAt()));
        Location l = c.location();
        if (l != null) {
            put(d, "region_id", l.regionId() == null ? null : String.valueOf(l.regionId()));
            put(d, "city", l.city());
            put(d, "postal_code", l.postalCode());
        }
        switch (a.details()) {
            case GoodsDetails g -> {
                d.put("price", g.price().amount());
                d.put("sale_type", g.saleType().name());
                put(d, "condition", g.condition() == null ? null : g.condition().name());
                put(d, "brand_id", g.brandId() == null ? null : String.valueOf(g.brandId().value()));
                g.attributes().forEach(x -> d.put(x.name(), x.value()));
            }
            case JobDetails j -> {
                d.put("employer", j.employer());
                d.put("contract_type", j.contractType().name());
                put(d, "application_deadline_epoch", epoch(j.applicationDeadline()));
                if (j.salaryRange() != null) {
                    put(d, "salary_min", j.salaryRange().min());
                    put(d, "salary_max", j.salaryRange().max());
                    d.put("currency", j.salaryRange().currency());
                }
            }
            case EventDetails e -> {
                d.put("starts_epoch", epoch(e.startsAt()));
                d.put("ends_epoch", epoch(e.endsAt()));
                put(d, "venue", e.venue().isEmpty() ? null : e.venue());
                put(d, "organizer", e.organizer().isEmpty() ? null : e.organizer());
                d.put("ticketed", e.ticketed() ? "yes" : "no");
                put(d, "ticket_price", e.ticketPrice() == null ? null : e.ticketPrice().amount());
            }
        }
        return new AnnounceDocument(a.id(), d);
    }

    private static Long epoch(LocalDateTime t) {
        return t == null ? null : t.toEpochSecond(ZoneOffset.UTC);
    }

    private static void put(Map<String, Object> d, String key, Object value) {
        if (value != null) {
            d.put(key, value);
        }
    }
}
