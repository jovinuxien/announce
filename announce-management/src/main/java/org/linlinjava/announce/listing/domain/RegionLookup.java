package org.linlinjava.announce.listing.domain;

import reactor.core.publisher.Mono;

/** {@code announce_region} by name. Empty when the city is unknown there (no geocoding). */
public interface RegionLookup {

    Mono<Integer> regionIdByCity(String city);
}
