package org.linlinjava.announce.goods.infrastructure.persistence.mybatis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.db.config.MyBatisConfig;
import org.linlinjava.announce.db.dao.reactive.LitemallBrandMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallCategoryMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallGoodsAttributeMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallGoodsMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallGoodsProductMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallGoodsSpecificationMapper;
import org.linlinjava.announce.db.domain.LitemallBrandExample;
import org.linlinjava.announce.db.domain.LitemallCategoryExample;
import org.linlinjava.announce.db.domain.LitemallGoodsAttributeExample;
import org.linlinjava.announce.db.domain.LitemallGoodsExample;
import org.linlinjava.announce.db.domain.LitemallGoodsProductExample;
import org.linlinjava.announce.db.domain.LitemallGoodsSpecificationExample;
import org.linlinjava.announce.goods.domain.announce.Announce;
import org.linlinjava.announce.goods.domain.announce.AnnounceDetails;
import org.linlinjava.announce.goods.domain.announce.AnnounceQuery;
import org.linlinjava.announce.goods.domain.announce.AnnounceStatus;
import org.linlinjava.announce.goods.domain.announce.Attribute;
import org.linlinjava.announce.goods.domain.announce.Location;
import org.linlinjava.announce.goods.domain.announce.Money;
import org.linlinjava.announce.goods.domain.announce.SaleType;
import org.linlinjava.announce.goods.domain.announce.SellerId;
import org.linlinjava.announce.goods.domain.announce.Specification;
import org.linlinjava.announce.goods.domain.announce.Variant;
import org.linlinjava.announce.goods.domain.brand.Brand;
import org.linlinjava.announce.goods.domain.category.Category;
import org.linlinjava.announce.goods.domain.category.CategoryId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import pro.chenggang.project.reactive.mybatis.support.r2dbc.spring.configuration.R2dbcMybatisAutoConfiguration;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round trips of the three repositories against the local announce_reactive_db, with only the persistence slice
 * loaded (no Eureka, Kafka or web). Rows are prefixed "it-" and removed afterwards.
 */
@SpringJUnitConfig(MyBatisRepositoriesIT.Slice.class)
@TestPropertySource(properties = {
        "spring.r2dbc.mybatis.r2dbc-url=r2dbc:mysql://localhost:3306/announce_reactive_db",
        "spring.r2dbc.mybatis.username=announce",
        "spring.r2dbc.mybatis.password=Calliste_1006",
        "spring.r2dbc.mybatis.pool.initial-size=1",
        "r2dbc.mybatis.mapper-locations=classpath:org/linlinjava/announce/db/dao/reactive/*.xml"})
class MyBatisRepositoriesIT {

    @Configuration
    @EnableConfigurationProperties
    @Import({R2dbcMybatisAutoConfiguration.class, MyBatisConfig.class,
            MyBatisAnnounceRepository.class, MyBatisCategoryRepository.class, MyBatisBrandRepository.class})
    static class Slice {
        @Bean
        TransactionalOperator transactionalOperator(ReactiveTransactionManager tm) {
            return TransactionalOperator.create(tm);
        }
    }

    static final String PREFIX = "it-";
    static final LocalDateTime NOW = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    static final SellerId SELLER = new SellerId(1);

    @Autowired MyBatisAnnounceRepository announces;
    @Autowired MyBatisCategoryRepository categories;
    @Autowired MyBatisBrandRepository brands;
    @Autowired LitemallGoodsMapper goodsMapper;
    @Autowired LitemallGoodsProductMapper productMapper;
    @Autowired LitemallGoodsAttributeMapper attributeMapper;
    @Autowired LitemallGoodsSpecificationMapper specificationMapper;
    @Autowired LitemallCategoryMapper categoryMapper;
    @Autowired LitemallBrandMapper brandMapper;

    @AfterEach
    void cleanUp() {
        LitemallGoodsExample g = new LitemallGoodsExample();
        g.or().andNameLike(PREFIX + "%");
        goodsMapper.selectByExampleSelective(g, org.linlinjava.announce.db.domain.LitemallGoods.Column.id)
                .flatMap(row -> {
                    LitemallGoodsProductExample p = new LitemallGoodsProductExample(); p.or().andGoodsIdEqualTo(row.getId());
                    LitemallGoodsAttributeExample a = new LitemallGoodsAttributeExample(); a.or().andGoodsIdEqualTo(row.getId());
                    LitemallGoodsSpecificationExample s = new LitemallGoodsSpecificationExample(); s.or().andGoodsIdEqualTo(row.getId());
                    return productMapper.deleteByExample(p).then(attributeMapper.deleteByExample(a)).then(specificationMapper.deleteByExample(s));
                })
                .then(goodsMapper.deleteByExample(g)).block();
        LitemallCategoryExample c = new LitemallCategoryExample(); c.or().andNameLike(PREFIX + "%");
        categoryMapper.deleteByExample(c).block();
        LitemallBrandExample b = new LitemallBrandExample(); b.or().andNameLike(PREFIX + "%");
        brandMapper.deleteByExample(b).block();
    }

    private Category newRoot(String name) {
        return categories.save(Category.root(PREFIX + name, "test", "", 1, NOW)).block();
    }

    @Test
    void announceRoundTripWithLocationChildrenAndLifecycle() {
        Category root = newRoot("cat-" + System.nanoTime());
        Location location = new Location(1, "Stockholm", "11122", new BigDecimal("59.329323"), new BigDecimal("18.068581"));
        AnnounceDetails details = new AnnounceDetails(PREFIX + "bike", "almost new", "<p>desc</p>", List.of("bike", "city"),
                List.of("http://x/1.jpg", "http://x/2.jpg"), "pcs", root.id(), null, SaleType.FIXED_PRICE, Money.of("1500"), location,
                List.of(new Attribute("Colour", "Red")), List.of(new Specification("Size", "M", "")));
        Announce draft = Announce.draft(SELLER, details, NOW);
        draft.addVariant(List.of("M"), Money.of("1500"), 2, "http://x/m.jpg", NOW);

        Announce saved = announces.save(draft).block();
        assertNotNull(saved.id());
        assertNotNull(saved.variants().get(0).id(), "variant id assigned on insert");

        StepVerifier.create(announces.findById(saved.id()))
                .assertNext(a -> {
                    assertEquals(AnnounceStatus.DRAFT, a.status());
                    assertEquals(location, a.details().location());
                    assertEquals(List.of("bike", "city"), a.details().keywords());
                    assertEquals(2, a.details().gallery().size());
                    assertEquals("Red", a.details().attributes().get(0).value());
                    assertEquals("Size", a.details().specifications().get(0).name());
                    assertEquals(1, a.variants().size());
                    assertEquals(2, a.variants().get(0).stock());
                    assertEquals("<p>desc</p>", a.details().description());
                })
                .verifyComplete();

        // publish + stock change persist; attributes are replaced, not duplicated
        Announce loaded = announces.findById(saved.id()).block();
        loaded.publish(Duration.ofDays(30), NOW);
        Variant v = loaded.variants().get(0);
        loaded.adjustStock(v.id(), -1, NOW);
        loaded.edit(new AnnounceDetails(PREFIX + "bike", "now cheaper", "<p>desc2</p>", List.of("bike"), List.of("http://x/1.jpg"), "pcs",
                root.id(), null, SaleType.FIXED_PRICE, Money.of("1400"), Location.inRegion(2, "Uppsala", null),
                List.of(new Attribute("Colour", "Blue")), List.of()), NOW.plusMinutes(1));
        announces.save(loaded).block();

        StepVerifier.create(announces.findById(saved.id()))
                .assertNext(a -> {
                    assertEquals(AnnounceStatus.PUBLISHED, a.status());
                    assertEquals(NOW.plusDays(30), a.expiresAt());
                    assertEquals(1, a.variants().get(0).stock());
                    assertEquals(1, a.details().attributes().size());
                    assertEquals("Blue", a.details().attributes().get(0).value());
                    assertEquals("Uppsala", a.details().location().city());
                    assertNull(a.details().location().latitude());
                    assertEquals("<p>desc2</p>", a.details().description());
                })
                .verifyComplete();

        // browse: public listing sees it, filtered by region and keyword; count agrees
        AnnounceQuery byRegion = new AnnounceQuery(null, null, SELLER.value(), 2, "bike", null, AnnounceStatus.PUBLISHED, 1, 10, "newest");
        StepVerifier.create(announces.browse(byRegion).filter(s -> s.id().equals(saved.id())))
                .assertNext(s -> {
                    assertEquals(PREFIX + "bike", s.title());
                    assertEquals(Money.of("1400"), s.price());
                    assertEquals("Uppsala", s.location().city());
                })
                .verifyComplete();
        assertTrue(announces.count(byRegion).block() >= 1);
        AnnounceQuery wrongRegion = new AnnounceQuery(null, null, SELLER.value(), 999, "bike", null, AnnounceStatus.PUBLISHED, 1, 10, "newest");
        StepVerifier.create(announces.browse(wrongRegion).filter(s -> s.id().equals(saved.id()))).verifyComplete();

        // expiry query: not due yet, then due
        StepVerifier.create(announces.findPublishedExpiredBefore(NOW.plusDays(29), 50).filter(a -> a.id().equals(saved.id()))).verifyComplete();
        StepVerifier.create(announces.findPublishedExpiredBefore(NOW.plusDays(31), 50).filter(a -> a.id().equals(saved.id())).count())
                .expectNext(1L).verifyComplete();
    }

    @Test
    void categoryAndBrandRoundTrips() {
        Category root = newRoot("root-" + System.nanoTime());
        Category child = categories.save(Category.childOf(root, PREFIX + "child", "", "", 2, NOW)).block();
        StepVerifier.create(categories.findChildren(root.id()).map(Category::id)).expectNext(child.id()).verifyComplete();
        StepVerifier.create(categories.existsVisible(child.id())).expectNext(true).verifyComplete();
        child.hide(NOW);
        categories.save(child).block();
        StepVerifier.create(categories.existsVisible(child.id())).expectNext(false).verifyComplete();
        StepVerifier.create(categories.findChildren(root.id())).verifyComplete();
        StepVerifier.create(categories.findById(child.id()).map(c -> c.level() + ":" + c.isHidden())).expectNext("L2:true").verifyComplete();

        Brand brand = brands.save(Brand.create(PREFIX + "brand-" + System.nanoTime(), "d", "", 3, NOW)).block();
        assertNotNull(brand.id());
        StepVerifier.create(brands.existsVisibleByName(brand.name())).expectNext(true).verifyComplete();
        StepVerifier.create(brands.findById(brand.id()).map(Brand::description)).expectNext("d").verifyComplete();
        brand.hide(NOW);
        brands.save(brand).block();
        StepVerifier.create(brands.existsVisibleByName(brand.name())).expectNext(false).verifyComplete();
    }
}
