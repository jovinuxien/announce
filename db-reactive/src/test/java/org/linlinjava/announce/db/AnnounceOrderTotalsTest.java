package org.linlinjava.announce.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.db.dao.reactive.AnnounceOrderMapper;
import org.linlinjava.announce.db.domain.AnnounceOrder;
import org.linlinjava.announce.db.domain.AnnounceOrderExample;
import org.linlinjava.announce.db.domain.AnnounceOrderStatusTotal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The hand-written {@code sumByStatus} of the order mapper (the payouts overview of the order module). Live MySQL. */
@SpringBootTest
class AnnounceOrderTotalsTest {

    /** No real member has this id: the rows belong to this test only. */
    private static final int SELLER = 987_654;

    @Autowired private AnnounceOrderMapper orders;

    @AfterEach
    void removeWhatWeWrote() {
        AnnounceOrderExample mine = new AnnounceOrderExample();
        mine.or().andSellerIdEqualTo(SELLER);
        orders.deleteByExample(mine).block();
    }

    private AnnounceOrder order(String id, String status, String fee, String payout, boolean deleted) {
        AnnounceOrder o = new AnnounceOrder();
        o.setId("it-totals-" + id);
        o.setAnnounceId("it-totals-announce");
        o.setSellerId(SELLER);
        o.setBuyerId(1);
        o.setDoor("FIXED_PRICE");
        o.setAgreedPrice(new BigDecimal(fee).add(new BigDecimal(payout)));
        o.setFeeAmount(new BigDecimal(fee));
        o.setPayoutAmount(new BigDecimal(payout));
        o.setCurrency("DKK");
        o.setStatus(status);
        o.setAddTime(LocalDateTime.now());
        o.setUpdateTime(LocalDateTime.now());
        o.setDeleted(deleted);
        return o;
    }

    @Test
    void sumsFeesAndPayoutsOfLiveOrdersPerStatus() {
        Map<String, AnnounceOrderStatusTotal> before = totals();
        StepVerifier.create(orders.insertSelective(order("1", "IT_SETTLED", "8.00", "92.00", false))).expectNext(1).verifyComplete();
        StepVerifier.create(orders.insertSelective(order("2", "IT_SETTLED", "1.50", "18.50", false))).expectNext(1).verifyComplete();
        StepVerifier.create(orders.insertSelective(order("3", "IT_DISPUTED", "2.00", "48.00", false))).expectNext(1).verifyComplete();
        StepVerifier.create(orders.insertSelective(order("4", "IT_SETTLED", "99.00", "1.00", true))).expectNext(1).verifyComplete();   // deleted: ignored

        Map<String, AnnounceOrderStatusTotal> after = totals();
        assertTrue(!before.containsKey("IT_SETTLED") && !before.containsKey("IT_DISPUTED"), "the test statuses are this test's own");
        AnnounceOrderStatusTotal settled = after.get("IT_SETTLED");
        assertEquals(2L, settled.getOrders());
        assertEquals(0, new BigDecimal("9.50").compareTo(settled.getFeeAmount()));
        assertEquals(0, new BigDecimal("110.50").compareTo(settled.getPayoutAmount()));
        AnnounceOrderStatusTotal disputed = after.get("IT_DISPUTED");
        assertEquals(1L, disputed.getOrders());
        assertEquals(0, new BigDecimal("48.00").compareTo(disputed.getPayoutAmount()));
    }

    private Map<String, AnnounceOrderStatusTotal> totals() {
        List<AnnounceOrderStatusTotal> rows = orders.sumByStatus().collectList().block();
        return rows.stream().collect(Collectors.toMap(AnnounceOrderStatusTotal::getStatus, Function.identity()));
    }
}
