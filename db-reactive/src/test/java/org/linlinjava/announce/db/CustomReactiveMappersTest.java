package org.linlinjava.announce.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.db.dao.reactive.GoodsProductMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallAdminMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallElasticMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallGoodsProductMapper;
import org.linlinjava.announce.db.dao.reactive.StatMapper;
import org.linlinjava.announce.db.domain.LitemallAdminExample;
import org.linlinjava.announce.db.domain.LitemallGoodsProduct;
import org.linlinjava.announce.db.domain.LitemallGoodsProductExample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The hand-written mappers plus the Integer[] JSON adapter (admin role ids). The order tables are read models of the order module now; their generated mappers are covered by the smoke test. */
@SpringBootTest
class CustomReactiveMappersTest {

    /** No announce has this id: the row belongs to this test only. */
    private static final String STOCK_ANNOUNCE_ID = "it-stock-announce";

    @Autowired private StatMapper statMapper;
    @Autowired private GoodsProductMapper goodsProductMapper;
    @Autowired private LitemallGoodsProductMapper litemallGoodsProductMapper;
    @Autowired private LitemallElasticMapper elasticMapper;
    @Autowired private LitemallAdminMapper adminMapper;

    @Test
    void statisticsReturnRowsKeyedByAlias() {
        StepVerifier.create(statMapper.statUser().next())
                .assertNext(row -> {
                    assertTrue(row.containsKey("day"));
                    assertTrue(row.containsKey("users"));
                })
                .verifyComplete();
        // no paid deals in the seed data: the stream just has to complete
        StepVerifier.create(statMapper.statOrder()).thenCancel().verify();
    }

    /** The seed has no variants (a plain classified has none), so the test brings its own product row and removes it. */
    @Test
    void stockMovesUpAndDownAndRefusesToGoNegative() {
        LitemallGoodsProduct product = new LitemallGoodsProduct();
        product.setAnnounceId(STOCK_ANNOUNCE_ID);
        product.setSpecifications(new String[] {"standard"});
        product.setPrice(new BigDecimal("10.00"));
        product.setNumber(7);
        product.setAddTime(LocalDateTime.now());
        product.setUpdateTime(LocalDateTime.now());
        product.setDeleted(false);
        StepVerifier.create(litemallGoodsProductMapper.insertSelective(product)).expectNext(1).verifyComplete();
        assertNotNull(product.getId());
        int before = product.getNumber();

        StepVerifier.create(goodsProductMapper.addStock(product.getId(), (short) 5)).expectNext(1).verifyComplete();
        StepVerifier.create(litemallGoodsProductMapper.selectByPrimaryKey(product.getId()).map(LitemallGoodsProduct::getNumber))
                .expectNext(before + 5).verifyComplete();
        StepVerifier.create(goodsProductMapper.reduceStock(product.getId(), (short) 5)).expectNext(1).verifyComplete();
        StepVerifier.create(goodsProductMapper.reduceStock(product.getId(), (short) (before + 1000)))
                .expectNext(0).verifyComplete();
        StepVerifier.create(litemallGoodsProductMapper.selectByPrimaryKey(product.getId()).map(LitemallGoodsProduct::getNumber))
                .expectNext(before).verifyComplete();
    }

    @AfterEach
    void removeStockRows() {
        LitemallGoodsProductExample example = new LitemallGoodsProductExample();
        example.or().andAnnounceIdEqualTo(STOCK_ANNOUNCE_ID);
        litemallGoodsProductMapper.deleteByExample(example).block();
    }

    @Test
    void elasticJoinRunsAgainstSeedData() {
        StepVerifier.create(elasticMapper.selectGoodsWithJoin("no-such-brand", "no-such-attribute")).verifyComplete();
    }

    @Test
    void adminRoleIdsAreReadAsIntegerArray() {
        LitemallAdminExample example = new LitemallAdminExample();
        example.or().andDeletedEqualTo(false);
        StepVerifier.create(adminMapper.selectPageByExample(example, 0, 1))
                .assertNext(admin -> assertNotNull(admin.getRoleIds()))
                .verifyComplete();
    }
}
