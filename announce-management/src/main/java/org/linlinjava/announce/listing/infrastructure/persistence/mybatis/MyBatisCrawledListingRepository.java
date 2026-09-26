package org.linlinjava.announce.listing.infrastructure.persistence.mybatis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.announce.db.dao.reactive.CrawledListingMapper;
import org.linlinjava.announce.db.domain.CrawledListingExample;
import org.linlinjava.announce.listing.domain.CrawledListing;
import org.linlinjava.announce.listing.domain.CrawledListingRepository;
import org.linlinjava.announce.listing.domain.ListingStatus;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDateTime;

/** {@link CrawledListingRepository} over the {@code crawled_listing} mapper of db-reactive. */
@Repository
public class MyBatisCrawledListingRepository implements CrawledListingRepository {

    private final CrawledListingMapper mapper;
    private final ListingRows rows;
    private final Clock clock;

    public MyBatisCrawledListingRepository(CrawledListingMapper mapper, ObjectMapper json, Clock clock) {
        this.mapper = mapper;
        this.rows = new ListingRows(json);
        this.clock = clock;
    }

    @Override
    public Mono<CrawledListing> findById(String id) {
        return mapper.selectByPrimaryKey(id).map(rows::toDomain);
    }

    @Override
    public Mono<Void> save(CrawledListing listing) {
        return mapper.upsert(rows.toRow(listing, LocalDateTime.now(clock))).then();
    }

    @Override
    public Mono<Void> touchSeen(String id, LocalDateTime seenAt) {
        return mapper.touchSeen(id, seenAt).then();
    }

    @Override
    public Mono<Void> updateStatus(String id, ListingStatus status, LocalDateTime now) {
        return mapper.updateStatus(id, status.name(), now).then();
    }

    @Override
    public Flux<String> staleIds(LocalDateTime before, int limit) {
        return mapper.selectStaleIds(before, limit);
    }

    @Override
    public Flux<CrawledListing> activePage(long offset, int limit) {
        CrawledListingExample example = new CrawledListingExample();
        example.or().andStatusEqualTo(ListingStatus.ACTIVE.name());
        example.setOrderByClause("id asc");
        return mapper.selectPageByExample(example, offset, limit).map(rows::toDomain);
    }

    @Override
    public Mono<CrawledListing> recordClick(String id, LocalDateTime now) {
        return mapper.incrementClickCount(id, now).filter(n -> n > 0).flatMap(n -> findById(id));
    }
}
