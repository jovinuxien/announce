package org.linlinjava.announce.listing.infrastructure.persistence.mybatis;

import org.linlinjava.announce.db.dao.reactive.ListingCategoryMapMapper;
import org.linlinjava.announce.db.domain.ListingCategoryMap;
import org.linlinjava.announce.db.domain.ListingCategoryMapKey;
import org.linlinjava.announce.listing.domain.ListingCategoryMapping;
import org.linlinjava.announce.listing.domain.ListingSource;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/** {@link ListingCategoryMapping} over the {@code listing_category_map} table. */
@Repository
public class MyBatisListingCategoryMapping implements ListingCategoryMapping {

    private final ListingCategoryMapMapper mapper;

    public MyBatisListingCategoryMapping(ListingCategoryMapMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Mono<Integer> categoryFor(ListingSource source, String sourceCategory) {
        ListingCategoryMapKey key = new ListingCategoryMapKey();
        key.setSource(source.name());
        key.setSourceCategory(sourceCategory);
        return mapper.selectByPrimaryKey(key).map(ListingCategoryMap::getCategoryId);
    }
}
