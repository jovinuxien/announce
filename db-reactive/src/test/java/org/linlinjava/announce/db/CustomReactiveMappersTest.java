package org.linlinjava.announce.db;

import org.junit.jupiter.api.Test;
import org.linlinjava.announce.db.dao.reactive.GoodsProductMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallAdminMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallElasticMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallGoodsProductMapper;
import org.linlinjava.announce.db.dao.reactive.LitemallOrderMapper;
import org.linlinjava.announce.db.dao.reactive.OrderMapper;
import org.linlinjava.announce.db.dao.reactive.StatMapper;
import org.linlinjava.announce.db.domain.LitemallAdminExample;
import org.linlinjava.announce.db.domain.LitemallGoodsProduct;
import org.linlinjava.announce.db.domain.LitemallGoodsProductExample;
import org.linlinjava.announce.db.domain.LitemallOrder;
import org.linlinjava.announce.db.domain.LitemallOrderExample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The four hand-written mappers plus the Integer[] JSON adapter (admin role ids). */
@SpringBootTest
class CustomReactiveMappersTest {

    @Autowired private StatMapper statMapper;
    @Autowired private OrderMapper orderMapper;
    @Autowired private LitemallOrderMapper litemallOrderMapper;
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
        // no paid orders in the seed data: the streams just have to complete
        StepVerifier.create(statMapper.statOrder()).thenCancel().verify();
        StepVerifier.create(statMapper.statGoods()).thenCancel().verify();
    }

    @Test
    void stockMovesUpAndDownAndRefusesToGoNegative() {
        LitemallGoodsProductExample example = new LitemallGoodsProductExample();
        example.or().andDeletedEqualTo(false);
        example.setOrderByClause("id asc");
        LitemallGoodsProduct product = litemallGoodsProductMapper.selectPageByExample(example, 0, 1).blockFirst();
        assertNotNull(product);
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

    @Test
    void orderListJoinsUserAndGoodsLinesAndOptimisticLockDetectsStaleUpdate() {
        LitemallOrder order = new LitemallOrder();
        order.setUserId(1);
        order.setOrderSn("it-order-" + System.nanoTime());
        order.setOrderStatus((short) 101);
        order.setConsignee("tester");
        order.setMobile("0000");
        order.setAddress("nowhere");
        order.setMessage("");
        order.setGoodsPrice(java.math.BigDecimal.TEN);
        order.setFreightPrice(java.math.BigDecimal.ZERO);
        order.setCouponPrice(java.math.BigDecimal.ZERO);
        order.setIntegralPrice(java.math.BigDecimal.ZERO);
        order.setGrouponPrice(java.math.BigDecimal.ZERO);
        order.setOrderPrice(java.math.BigDecimal.TEN);
        order.setActualPrice(java.math.BigDecimal.TEN);
        order.setAddTime(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        order.setUpdateTime(order.getAddTime());
        order.setDeleted(false);
        StepVerifier.create(litemallOrderMapper.insertSelective(order)).expectNext(1).verifyComplete();
        try {
            StepVerifier.create(orderMapper.getOrderIds("o.id = " + order.getId(), "o.id desc"))
                    .assertNext(row -> assertEquals(order.getId(), ((Number) row.get("id")).intValue()))
                    .verifyComplete();
            StepVerifier.create(orderMapper.getOrderList("o.id = " + order.getId(), null))
                    .assertNext(vo -> {
                        assertEquals(order.getOrderSn(), vo.getOrderSn());
                        assertEquals(1, vo.getUserId());
                        assertNotNull(vo.getGoodsVoList());
                    })
                    .verifyComplete();

            LitemallOrder patch = new LitemallOrder();
            patch.setId(order.getId());
            patch.setMessage("updated");
            patch.setUpdateTime(order.getUpdateTime().plusSeconds(1));
            StepVerifier.create(orderMapper.updateWithOptimisticLocker(order.getUpdateTime(), patch)).expectNext(1).verifyComplete();
            StepVerifier.create(orderMapper.updateWithOptimisticLocker(order.getUpdateTime(), patch)).expectNext(0).verifyComplete();
        } finally {
            LitemallOrderExample byId = new LitemallOrderExample();
            byId.or().andIdEqualTo(order.getId());
            litemallOrderMapper.deleteByExample(byId).block();
        }
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
