package org.linlinjava.announce.listing.infrastructure.persistence.mybatis;

import org.linlinjava.announce.db.dao.reactive.LitemallRegionMapper;
import org.linlinjava.announce.db.domain.LitemallRegion;
import org.linlinjava.announce.db.domain.LitemallRegionExample;
import org.linlinjava.announce.listing.domain.RegionLookup;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/** {@link RegionLookup} over {@code announce_region}: the first region whose name equals the city, if any. */
@Repository
public class MyBatisRegionLookup implements RegionLookup {

    private final LitemallRegionMapper mapper;

    public MyBatisRegionLookup(LitemallRegionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Mono<Integer> regionIdByCity(String city) {
        LitemallRegionExample example = new LitemallRegionExample();
        example.or().andNameEqualTo(city);
        example.setOrderByClause("type desc, id asc");
        return mapper.selectPageByExample(example, 0, 1).next().map(LitemallRegion::getId);
    }
}
