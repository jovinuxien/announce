package org.linlinjava.announce.listing.infrastructure.persistence.mybatis;

import org.linlinjava.announce.db.dao.reactive.ListingImportRunMapper;
import org.linlinjava.announce.db.domain.ListingImportRunExample;
import org.linlinjava.announce.listing.domain.ListingImportRun;
import org.linlinjava.announce.listing.domain.ListingImportRunRepository;
import org.linlinjava.announce.listing.domain.ListingSource;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/** {@link ListingImportRunRepository} over the {@code listing_import_run} mapper of db-reactive. */
@Repository
public class MyBatisListingImportRunRepository implements ListingImportRunRepository {

    private final ListingImportRunMapper mapper;

    public MyBatisListingImportRunRepository(ListingImportRunMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Mono<Void> record(ListingImportRun run) {
        org.linlinjava.announce.db.domain.ListingImportRun r = new org.linlinjava.announce.db.domain.ListingImportRun();
        r.setSource(run.source().name());
        r.setFile(run.file());
        r.setStartedAt(run.startedAt());
        r.setFinishedAt(run.finishedAt());
        r.setSeen((int) run.seen());
        r.setInserted((int) run.inserted());
        r.setUpdated((int) run.updated());
        r.setUnchanged((int) run.unchanged());
        r.setRejected((int) run.rejected());
        r.setGood((int) run.good());
        r.setWeak((int) run.weak());
        r.setReject((int) run.reject());
        return mapper.insert(r).then();
    }

    @Override
    public Mono<ListingImportRun> latest(ListingSource source) {
        ListingImportRunExample example = new ListingImportRunExample();
        example.or().andSourceEqualTo(source.name());
        example.setOrderByClause("finished_at desc, id desc");
        return mapper.selectPageByExample(example, 0, 1).next()
                .map(r -> new ListingImportRun(source, r.getFile(), r.getStartedAt(), r.getFinishedAt(), r.getSeen(), r.getInserted(),
                        r.getUpdated(), r.getUnchanged(), r.getRejected(), r.getGood(), r.getWeak(), r.getReject()));
    }
}
