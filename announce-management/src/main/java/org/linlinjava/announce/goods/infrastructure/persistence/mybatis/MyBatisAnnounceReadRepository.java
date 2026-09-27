package org.linlinjava.announce.goods.infrastructure.persistence.mybatis;

import org.linlinjava.announce.db.dao.reactive.AnnounceBaseMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceEventDetailMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceGoodsDetailMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceJobDetailMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallGoodsAttributeMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallGoodsProductMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallGoodsSpecificationMapper;
import org.linlinjava.announce.db.domain.AnnounceBase;
import org.linlinjava.announce.db.domain.AnnounceBaseExample;
import org.linlinjava.announce.db.domain.LitemallGoodsAttributeExample;
import org.linlinjava.announce.db.domain.LitemallGoodsProductExample;
import org.linlinjava.announce.db.domain.LitemallGoodsSpecificationExample;
import org.linlinjava.announce.goods.domain.announce.Announce;
import org.linlinjava.announce.goods.domain.announce.AnnounceKind;
import org.linlinjava.announce.goods.domain.announce.AnnounceQuery;
import org.linlinjava.announce.goods.domain.announce.AnnounceReadRepository;
import org.linlinjava.announce.goods.domain.announce.AnnounceReadRepository.Applied;
import org.linlinjava.announce.goods.domain.announce.AnnounceStatus;
import org.linlinjava.announce.goods.domain.announce.AnnounceSummary;
import org.linlinjava.announce.goods.domain.announce.Kind;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/** {@link AnnounceReadRepository} over the announce read-model mappers of db-reactive. Never touches the event store. */
@Repository
public class MyBatisAnnounceReadRepository implements AnnounceReadRepository {

    private final AnnounceBaseMapper announces;
    private final AnnounceGoodsDetailMapper goods;
    private final AnnounceJobDetailMapper jobs;
    private final AnnounceEventDetailMapper events;
    private final LitemallGoodsProductMapper products;
    private final LitemallGoodsAttributeMapper attributes;
    private final LitemallGoodsSpecificationMapper specifications;

    public MyBatisAnnounceReadRepository(AnnounceBaseMapper announces, AnnounceGoodsDetailMapper goods, AnnounceJobDetailMapper jobs,
                                         AnnounceEventDetailMapper events, LitemallGoodsProductMapper products,
                                         LitemallGoodsAttributeMapper attributes, LitemallGoodsSpecificationMapper specifications) {
        this.announces = announces;
        this.goods = goods;
        this.jobs = jobs;
        this.events = events;
        this.products = products;
        this.attributes = attributes;
        this.specifications = specifications;
    }

    @Override
    public Mono<Announce> findById(String id) {
        return findApplied(id).map(Applied::announce);
    }

    @Override
    public Mono<Applied> findApplied(String id) {
        return announces.selectByPrimaryKey(id)
                .filter(row -> !Boolean.TRUE.equals(row.getDeleted()))
                .flatMap(row -> details(row).map(d -> new Applied(AnnounceRows.toAnnounce(row, d), row.getLastEventId())));
    }

    private Mono<AnnounceKind> details(AnnounceBase row) {
        String id = row.getId();
        return switch (Kind.valueOf(row.getKind())) {
            case GOODS -> {
                LitemallGoodsProductExample p = new LitemallGoodsProductExample();
                p.or().andAnnounceIdEqualTo(id).andDeletedEqualTo(false);
                p.setOrderByClause("id asc");
                LitemallGoodsAttributeExample a = new LitemallGoodsAttributeExample();
                a.or().andAnnounceIdEqualTo(id).andDeletedEqualTo(false);
                a.setOrderByClause("id asc");
                LitemallGoodsSpecificationExample s = new LitemallGoodsSpecificationExample();
                s.or().andAnnounceIdEqualTo(id).andDeletedEqualTo(false);
                s.setOrderByClause("id asc");
                yield Mono.zip(goods.selectByPrimaryKey(id).map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty()),
                                products.selectByExample(p).collectList(), attributes.selectByExample(a).collectList(),
                                specifications.selectByExample(s).collectList())
                        .map(t -> AnnounceRows.toGoods(t.getT1().orElse(null), t.getT2(), t.getT3(), t.getT4()));
            }
            case JOB -> jobs.selectByPrimaryKey(id).map(AnnounceRows::toJob);
            case EVENT -> events.selectByPrimaryKey(id).map(AnnounceRows::toEvent);
        };
    }

    @Override
    public Flux<AnnounceSummary> browse(AnnounceQuery q) {
        return announces.selectSummaries(name(q.kind()), q.categoryIds(), q.brandId(), q.sellerId(), q.regionId(), q.keywords(),
                        q.saleType() == null ? null : q.saleType().name(), q.status() == null ? null : q.status().name(),
                        q.orderBy(), q.offset(), q.size())
                .map(AnnounceRows::toSummary);
    }

    @Override
    public Mono<Long> count(AnnounceQuery q) {
        return announces.countSummaries(name(q.kind()), q.categoryIds(), q.brandId(), q.sellerId(), q.regionId(), q.keywords(),
                q.saleType() == null ? null : q.saleType().name(), q.status() == null ? null : q.status().name());
    }

    @Override
    public Flux<String> findPublishedExpiredBefore(LocalDateTime now, int limit) {
        AnnounceBaseExample example = new AnnounceBaseExample();
        example.or().andStatusEqualTo(AnnounceStatus.PUBLISHED.name()).andExpiresAtLessThan(now).andDeletedEqualTo(false);
        example.setOrderByClause("expires_at asc");
        return announces.selectPageByExampleSelective(example, 0, Math.max(1, limit), AnnounceBase.Column.id).map(AnnounceBase::getId);
    }

    private static String name(Kind kind) {
        return kind == null ? null : kind.name();
    }
}
