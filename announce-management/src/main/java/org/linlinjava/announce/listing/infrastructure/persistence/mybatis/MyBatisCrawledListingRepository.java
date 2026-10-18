package org.linlinjava.announce.listing.infrastructure.persistence.mybatis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.announce.db.dao.reactive.CrawledListingMapper;
import org.linlinjava.announce.db.domain.CrawledListingExample;
import org.linlinjava.announce.listing.domain.CrawledListing;
import org.linlinjava.announce.listing.domain.CrawledListingRepository;
import org.linlinjava.announce.listing.domain.InventoryCount;
import org.linlinjava.announce.listing.domain.ListingGrade;
import org.linlinjava.announce.listing.domain.ListingQuality;
import org.linlinjava.announce.listing.domain.ListingSource;
import org.linlinjava.announce.listing.domain.ListingStatus;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

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

    @Override
    public Flux<CrawledListing> forLivenessCheck(int limit) {
        return mapper.selectForLivenessCheck(limit).map(rows::toDomain);
    }

    @Override
    public Mono<Void> updateLiveness(String id, LocalDateTime checkedAt, Boolean urlOk, Boolean imageOk) {
        return mapper.updateLiveness(id, checkedAt, urlOk, imageOk).then();
    }

    @Override
    public Mono<Void> updateQuality(String id, ListingQuality quality, LocalDateTime now) {
        return mapper.updateQuality(id, quality.grade().name(), rows.issues(quality), now).then();
    }

    @Override
    public Flux<InventoryCount> inventoryCounts() {
        return mapper.selectInventoryCounts().map(MyBatisCrawledListingRepository::toCount);
    }

    private static InventoryCount toCount(Map<String, Object> m) {
        Object grade = m.get("quality_grade");
        return new InventoryCount(ListingSource.valueOf((String) m.get("source")), ListingStatus.valueOf((String) m.get("status")),
                grade == null ? null : ListingGrade.valueOf((String) grade), number(m.get("total")),
                number(m.get("missing_image")), number(m.get("dead_link")), number(m.get("checked")));
    }

    private static long number(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }
}
