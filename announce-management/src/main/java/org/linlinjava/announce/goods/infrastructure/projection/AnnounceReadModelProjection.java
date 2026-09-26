package org.linlinjava.announce.goods.infrastructure.projection;

import org.linlinjava.announce.cqrs.command.Subjects;
import org.linlinjava.announce.cqrs.readside.EventEnvelope;
import org.linlinjava.announce.cqrs.readside.ProjectionHandlers;
import org.linlinjava.announce.cqrs.readside.ReactiveProjection;
import org.linlinjava.announce.db.dao.reactive.AnnounceBaseMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceEventDetailMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceGoodsDetailMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceJobDetailMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallGoodsAttributeMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallGoodsProductMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallGoodsSpecificationMapper;
import org.linlinjava.announce.db.domain.AnnounceBase;
import org.linlinjava.announce.db.domain.LitemallGoodsAttributeExample;
import org.linlinjava.announce.db.domain.LitemallGoodsProductExample;
import org.linlinjava.announce.db.domain.LitemallGoodsSpecificationExample;
import org.linlinjava.announce.goods.domain.announce.Announce;
import org.linlinjava.announce.goods.domain.announce.AnnounceKind;
import org.linlinjava.announce.goods.domain.announce.AnnounceStatus;
import org.linlinjava.announce.goods.domain.announce.EventDetails;
import org.linlinjava.announce.goods.domain.announce.GoodsDetails;
import org.linlinjava.announce.goods.domain.announce.JobDetails;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceEdited;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceExpired;
import org.linlinjava.announce.goods.domain.announce.events.AnnouncePosted;
import org.linlinjava.announce.goods.domain.announce.events.AnnouncePublished;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceSold;
import org.linlinjava.announce.goods.domain.announce.events.AnnounceWithdrawn;
import org.linlinjava.announce.goods.infrastructure.persistence.mybatis.AnnounceRows;
import org.springframework.r2dbc.core.DatabaseClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Writes the read-model tables ({@code announce}, {@code announce_goods_detail}, {@code announce_job},
 * {@code announce_event} and the goods children) from the events of {@code /announce/*}. Group
 * {@code announce-read-model}; its checkpoint lives in {@code cqrs_readside_progress} next to the tables.
 *
 * <p>At-least-once: every handler is idempotent (upsert keyed by the announce id, children replaced as a set,
 * status transitions repeatable) and records the event id in {@code last_event_id}. The tables come from the schema
 * scripts, so {@code prepare()} does nothing; {@code reset()} empties them for a rebuild.
 */
@Component
public class AnnounceReadModelProjection implements ReactiveProjection {

    private static final Logger log = LoggerFactory.getLogger(AnnounceReadModelProjection.class);

    public static final String GROUP = "announce-read-model";

    private final AnnounceBaseMapper announces;
    private final AnnounceGoodsDetailMapper goods;
    private final AnnounceJobDetailMapper jobs;
    private final AnnounceEventDetailMapper events;
    private final LitemallGoodsProductMapper products;
    private final LitemallGoodsAttributeMapper attributes;
    private final LitemallGoodsSpecificationMapper specifications;
    private final TransactionalOperator tx;
    private final DatabaseClient db;

    public AnnounceReadModelProjection(AnnounceBaseMapper announces, AnnounceGoodsDetailMapper goods, AnnounceJobDetailMapper jobs,
                                       AnnounceEventDetailMapper events, LitemallGoodsProductMapper products,
                                       LitemallGoodsAttributeMapper attributes, LitemallGoodsSpecificationMapper specifications,
                                       TransactionalOperator tx, DatabaseClient db) {
        this.announces = announces;
        this.goods = goods;
        this.jobs = jobs;
        this.events = events;
        this.products = products;
        this.attributes = attributes;
        this.specifications = specifications;
        this.tx = tx;
        this.db = db;
    }

    @Override
    public String group() {
        return GROUP;
    }

    @Override
    public void configure(ProjectionHandlers handlers) {
        handlers.on(AnnouncePosted.class, (event, envelope) -> {
            Announce state = Announce.apply(idOf(envelope), event);
            return tx.transactional(announces.upsert(AnnounceRows.toBaseRow(state, envelope.eventId()))
                    .then(writeDetails(state.id(), event.details(), event.at()))).then();
        });
        handlers.on(AnnounceEdited.class, (event, envelope) -> {
            String id = idOf(envelope);
            AnnounceBase row = new AnnounceBase();
            row.setId(id);
            row.setUpdateTime(event.at());
            row.setLastEventId(envelope.eventId());
            AnnounceRows.fillContent(row, event.content());
            return tx.transactional(announces.updateContent(row).then(writeDetails(id, event.details(), event.at()))).then();
        });
        handlers.on(AnnouncePublished.class, (event, envelope) -> status(envelope, AnnounceStatus.PUBLISHED, event.at(), event.at(), event.expiresAt()));
        handlers.on(AnnounceWithdrawn.class, (event, envelope) -> status(envelope, AnnounceStatus.WITHDRAWN, event.at(), null, null));
        handlers.on(AnnounceSold.class, (event, envelope) -> status(envelope, AnnounceStatus.SOLD, event.at(), null, null));
        handlers.on(AnnounceExpired.class, (event, envelope) -> status(envelope, AnnounceStatus.EXPIRED, event.at(), null, null));
    }

    @Override
    public Mono<Void> reset() {
        return Flux.just("announce_goods_product", "announce_goods_attribute", "announce_goods_specification",
                        "announce_goods_detail", "announce_job", "announce_event", "announce")
                .concatMap(table -> db.sql("delete from " + table).then())
                .then();
    }

    private Mono<Void> status(EventEnvelope envelope, AnnounceStatus status, LocalDateTime at, LocalDateTime publishedAt, LocalDateTime expiresAt) {
        AnnounceBase row = new AnnounceBase();
        row.setId(idOf(envelope));
        row.setStatus(status.name());
        row.setPublishedAt(publishedAt);
        row.setExpiresAt(expiresAt);
        row.setUpdateTime(at);
        row.setLastEventId(envelope.eventId());
        return announces.updateStatus(row)
                .doOnNext(n -> log.debug("announce {}: status {} after event {} ({} row)", row.getId(), status, envelope.eventId(), n))
                .then();
    }

    private Mono<Void> writeDetails(String id, AnnounceKind details, LocalDateTime at) {
        return switch (details) {
            case GoodsDetails g -> goods.upsert(AnnounceRows.toGoodsRow(id, g)).then(replaceChildren(id, g, at));
            case JobDetails j -> jobs.upsert(AnnounceRows.toJobRow(id, j)).then();
            case EventDetails e -> events.upsert(AnnounceRows.toEventRow(id, e)).then();
        };
    }

    private Mono<Void> replaceChildren(String id, GoodsDetails g, LocalDateTime at) {
        LitemallGoodsProductExample p = new LitemallGoodsProductExample();
        p.or().andAnnounceIdEqualTo(id);
        LitemallGoodsAttributeExample a = new LitemallGoodsAttributeExample();
        a.or().andAnnounceIdEqualTo(id);
        LitemallGoodsSpecificationExample s = new LitemallGoodsSpecificationExample();
        s.or().andAnnounceIdEqualTo(id);
        return products.deleteByExample(p).then(attributes.deleteByExample(a)).then(specifications.deleteByExample(s))
                .thenMany(Flux.fromIterable(g.variants()).concatMap(v -> products.insertSelective(AnnounceRows.toProductRow(id, v, at))))
                .thenMany(Flux.fromIterable(g.attributes()).concatMap(x -> attributes.insertSelective(AnnounceRows.toAttributeRow(id, x, at))))
                .thenMany(Flux.fromIterable(g.specifications()).concatMap(x -> specifications.insertSelective(AnnounceRows.toSpecificationRow(id, x, at))))
                .then();
    }

    private static String idOf(EventEnvelope envelope) {
        return Subjects.idOf(envelope.subject());
    }
}
