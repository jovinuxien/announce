package org.linlinjava.announce.goods.infrastructure.persistence.mybatis;

import org.linlinjava.announce.db.dao.reactive.LitemallGoodsAttributeMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallGoodsMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallGoodsProductMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallGoodsSpecificationMapper;
import org.linlinjava.announce.db.domain.LitemallGoods;
import org.linlinjava.announce.db.domain.LitemallGoodsAttribute;
import org.linlinjava.announce.db.domain.LitemallGoodsAttributeExample;
import org.linlinjava.announce.db.domain.LitemallGoodsExample;
import org.linlinjava.announce.db.domain.LitemallGoodsProduct;
import org.linlinjava.announce.db.domain.LitemallGoodsProductExample;
import org.linlinjava.announce.db.domain.LitemallGoodsSpecification;
import org.linlinjava.announce.db.domain.LitemallGoodsSpecificationExample;
import org.linlinjava.announce.goods.domain.announce.Announce;
import org.linlinjava.announce.goods.domain.announce.AnnounceId;
import org.linlinjava.announce.goods.domain.announce.AnnounceQuery;
import org.linlinjava.announce.goods.domain.announce.AnnounceRepository;
import org.linlinjava.announce.goods.domain.announce.AnnounceStatus;
import org.linlinjava.announce.goods.domain.announce.AnnounceSummary;
import org.linlinjava.announce.goods.domain.announce.Attribute;
import org.linlinjava.announce.goods.domain.announce.Specification;
import org.linlinjava.announce.goods.domain.announce.Variant;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import static org.linlinjava.announce.goods.infrastructure.persistence.mybatis.AnnounceRows.*;

/**
 * {@link AnnounceRepository} over the reactive announce_goods mappers of db-reactive.
 * One announce = one goods row plus its product (variant), attribute and specification rows.
 */
@Repository
public class MyBatisAnnounceRepository implements AnnounceRepository {

    private static final LitemallGoods.Column[] SUMMARY_COLUMNS = {
            LitemallGoods.Column.id, LitemallGoods.Column.sellerId, LitemallGoods.Column.name, LitemallGoods.Column.brief,
            LitemallGoods.Column.picUrl, LitemallGoods.Column.retailPrice, LitemallGoods.Column.saleType, LitemallGoods.Column.status,
            LitemallGoods.Column.categoryId, LitemallGoods.Column.regionId, LitemallGoods.Column.city, LitemallGoods.Column.postalCode,
            LitemallGoods.Column.latitude, LitemallGoods.Column.longitude, LitemallGoods.Column.addTime, LitemallGoods.Column.expireTime};

    private final LitemallGoodsMapper goods;
    private final LitemallGoodsProductMapper products;
    private final LitemallGoodsAttributeMapper attributes;
    private final LitemallGoodsSpecificationMapper specifications;
    private final TransactionalOperator tx;

    public MyBatisAnnounceRepository(LitemallGoodsMapper goods, LitemallGoodsProductMapper products,
                                     LitemallGoodsAttributeMapper attributes, LitemallGoodsSpecificationMapper specifications,
                                     TransactionalOperator tx) {
        this.goods = goods;
        this.products = products;
        this.attributes = attributes;
        this.specifications = specifications;
        this.tx = tx;
    }

    @Override
    public Mono<Announce> findById(AnnounceId id) {
        return goods.selectByPrimaryKey(id.value())
                .filter(row -> !Boolean.TRUE.equals(row.getDeleted()))
                .flatMap(this::withChildren);
    }

    @Override
    public Mono<Announce> save(Announce announce) {
        Mono<Announce> work = announce.id() == null ? insert(announce) : update(announce);
        return tx.transactional(work);
    }

    @Override
    public Flux<Announce> findPublishedExpiredBefore(LocalDateTime now, int limit) {
        LitemallGoodsExample example = new LitemallGoodsExample();
        example.or().andStatusEqualTo(AnnounceStatus.PUBLISHED.name()).andExpireTimeLessThan(now).andDeletedEqualTo(false);
        example.setOrderByClause("expire_time asc");
        return goods.selectPageByExample(example, 0, Math.max(1, limit)).concatMap(this::withChildren);
    }

    @Override
    public Flux<AnnounceSummary> browse(AnnounceQuery query) {
        LitemallGoodsExample example = browseExample(query);
        example.setOrderByClause(query.orderBy());
        return goods.selectPageByExampleSelective(example, query.offset(), query.size(), SUMMARY_COLUMNS).map(AnnounceRows::toSummary);
    }

    @Override
    public Mono<Long> count(AnnounceQuery query) {
        return goods.countByExample(browseExample(query));
    }

    // ------------------------------------------------------------------ internals

    private Mono<Announce> withChildren(LitemallGoods row) {
        Mono<List<LitemallGoodsProduct>> productRows = products.selectByExample(childrenOf(new LitemallGoodsProductExample(), row.getId(),
                e -> e.or().andGoodsIdEqualTo(row.getId()).andDeletedEqualTo(false))).collectList();
        Mono<List<LitemallGoodsAttribute>> attributeRows = attributes.selectByExample(childrenOf(new LitemallGoodsAttributeExample(), row.getId(),
                e -> e.or().andGoodsIdEqualTo(row.getId()).andDeletedEqualTo(false))).collectList();
        Mono<List<LitemallGoodsSpecification>> specificationRows = specifications.selectByExample(childrenOf(new LitemallGoodsSpecificationExample(), row.getId(),
                e -> e.or().andGoodsIdEqualTo(row.getId()).andDeletedEqualTo(false))).collectList();
        return Mono.zip(productRows, attributeRows, specificationRows)
                .map(t -> toAnnounce(row, t.getT1(), t.getT2(), t.getT3()));
    }

    private static <E> E childrenOf(E example, Integer goodsId, Consumer<E> criteria) {
        criteria.accept(example);
        return example;
    }

    private Mono<Announce> insert(Announce announce) {
        LitemallGoods row = toNewRow(announce);
        return goods.insertSelective(row)
                .then(Mono.fromRunnable(() -> announce.assignId(new AnnounceId(row.getId()))))
                .then(Mono.defer(() -> insertChildren(announce)))
                .thenReturn(announce);
    }

    private Mono<Announce> update(Announce announce) {
        Integer goodsId = announce.id().value();
        Mono<Void> variants = Flux.fromIterable(announce.variants())
                .concatMap(v -> {
                    if (v.isNew()) {
                        LitemallGoodsProduct p = toNewProductRow(goodsId, v, announce.updatedAt());
                        return products.insertSelective(p).then(Mono.fromRunnable(() -> v.assignId(p.getId())));
                    }
                    return products.updateByPrimaryKeySelective(toProductPatch(v, announce.updatedAt())).then();
                })
                .then();
        LitemallGoodsAttributeExample byGoodsA = new LitemallGoodsAttributeExample();
        byGoodsA.or().andGoodsIdEqualTo(goodsId);
        LitemallGoodsSpecificationExample byGoodsS = new LitemallGoodsSpecificationExample();
        byGoodsS.or().andGoodsIdEqualTo(goodsId);
        Mono<Void> valueChildren = attributes.deleteByExample(byGoodsA)
                .then(specifications.deleteByExample(byGoodsS))
                .then(insertValueChildren(announce));
        return goods.updateAnnounce(toPatchRow(announce))
                .then(variants)
                .then(valueChildren)
                .thenReturn(announce);
    }

    private Mono<Void> insertChildren(Announce announce) {
        Integer goodsId = announce.id().value();
        Mono<Void> variants = Flux.fromIterable(announce.variants())
                .concatMap(v -> {
                    LitemallGoodsProduct p = toNewProductRow(goodsId, v, announce.createdAt());
                    return products.insertSelective(p).then(Mono.fromRunnable(() -> v.assignId(p.getId())));
                })
                .then();
        return variants.then(insertValueChildren(announce));
    }

    private Mono<Void> insertValueChildren(Announce announce) {
        Integer goodsId = announce.id().value();
        LocalDateTime at = announce.updatedAt();
        Flux<Integer> a = Flux.fromIterable(announce.details().attributes()).concatMap(x -> attributes.insertSelective(toAttributeRow(goodsId, x, at)));
        Flux<Integer> s = Flux.fromIterable(announce.details().specifications()).concatMap(x -> specifications.insertSelective(toSpecificationRow(goodsId, x, at)));
        return a.then(s.then());
    }

    /** Keyword search is an OR over title and keywords; Example ORs whole criteria groups, so the common filters are repeated per group. */
    private static LitemallGoodsExample browseExample(AnnounceQuery q) {
        LitemallGoodsExample example = new LitemallGoodsExample();
        if (q.keywords() == null) {
            common(example.or(), q);
        } else {
            common(example.or().andNameLike("%" + q.keywords() + "%"), q);
            common(example.or().andKeywordsLike("%" + q.keywords() + "%"), q);
        }
        return example;
    }

    private static void common(LitemallGoodsExample.Criteria c, AnnounceQuery q) {
        c.andDeletedEqualTo(false);
        if (q.status() != null) c.andStatusEqualTo(q.status().name());
        if (!q.categoryIds().isEmpty()) c.andCategoryIdIn(q.categoryIds());
        if (q.brandId() != null) c.andBrandIdEqualTo(q.brandId());
        if (q.sellerId() != null) c.andSellerIdEqualTo(q.sellerId());
        if (q.regionId() != null) c.andRegionIdEqualTo(q.regionId());
        if (q.saleType() != null) c.andSaleTypeEqualTo(q.saleType().name());
    }
}
