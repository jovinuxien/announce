package org.linlinjava.announce.goods.infrastructure.persistence.mybatis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.db.config.MyBatisConfig;
import org.linlinjava.announce.db.dao.reactive.LitemallBrandMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallCategoryMapper;
import org.linlinjava.announce.db.domain.LitemallBrandExample;
import org.linlinjava.announce.db.domain.LitemallCategoryExample;
import org.linlinjava.announce.goods.domain.announce.Kind;
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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Round trips of the category and brand repositories against the local announce_reactive_db, with only the persistence slice
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
            MyBatisCategoryRepository.class, MyBatisBrandRepository.class})
    static class Slice {
        @Bean
        TransactionalOperator transactionalOperator(ReactiveTransactionManager tm) {
            return TransactionalOperator.create(tm);
        }
    }

    static final String PREFIX = "it-";
    static final LocalDateTime NOW = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

    @Autowired MyBatisCategoryRepository categories;
    @Autowired MyBatisBrandRepository brands;
    @Autowired LitemallCategoryMapper categoryMapper;
    @Autowired LitemallBrandMapper brandMapper;

    @AfterEach
    void cleanUp() {
        LitemallCategoryExample c = new LitemallCategoryExample(); c.or().andNameLike(PREFIX + "%");
        categoryMapper.deleteByExample(c).block();
        LitemallBrandExample b = new LitemallBrandExample(); b.or().andNameLike(PREFIX + "%");
        brandMapper.deleteByExample(b).block();
    }

    private Category newRoot(String name) {
        return categories.save(Category.root(Kind.GOODS, PREFIX + name, "test", "", 1, NOW)).block();
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
