package org.linlinjava.announce.goods.domain.announce;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * The kind-specific part of an announce. Exactly one implementation per {@link Kind}; the JSON form carries the
 * kind as the {@code kind} discriminator so events and API payloads read the same way.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = GoodsDetails.class, name = "GOODS"),
        @JsonSubTypes.Type(value = JobDetails.class, name = "JOB"),
        @JsonSubTypes.Type(value = EventDetails.class, name = "EVENT")})
public sealed interface AnnounceKind permits GoodsDetails, JobDetails, EventDetails {

    Kind kind();

    /** How the item changes hands. Only goods are sold; jobs and events normally carry none. */
    SaleType saleType();

    /** Only goods can be bid on (auction context). */
    @JsonIgnore
    default boolean isBiddable() {
        return false;
    }
}
