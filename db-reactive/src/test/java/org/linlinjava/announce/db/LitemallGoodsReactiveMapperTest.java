package org.linlinjava.announce.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.db.dao.reactive.LitemallGoodsMapper;
import org.linlinjava.announce.db.domain.LitemallGoods;
import org.linlinjava.announce.db.domain.LitemallGoodsExample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Goods is the table with a JSON array column ({@code gallery}) and a BLOB column ({@code detail}). */
@SpringBootTest
class LitemallGoodsReactiveMapperTest {

    private static final String PREFIX = "it-goods-";

    @Autowired
    private LitemallGoodsMapper goodsMapper;

    @AfterEach
    void cleanUp() {
        LitemallGoodsExample example = new LitemallGoodsExample();
        example.or().andNameLike(PREFIX + "%");
        goodsMapper.deleteByExample(example).block();
    }

    @Test
    void seededGalleryIsReadAsStringArray() {
        LitemallGoodsExample example = new LitemallGoodsExample();
        example.or().andDeletedEqualTo(false).andGalleryIsNotNull();
        example.setOrderByClause("id asc");
        StepVerifier.create(goodsMapper.selectPageByExample(example, 0, 1))
                .assertNext(g -> {
                    assertNotNull(g.getGallery());
                    assertTrue(g.getGallery().length > 0);
                    assertTrue(g.getGallery()[0].startsWith("http"));
                    assertNull(g.getDetail(), "detail is a BLOB column and must not be loaded by the base result map");
                })
                .verifyComplete();
    }

    @Test
    void galleryAndDetailRoundTrip() {
        LitemallGoods goods = new LitemallGoods();
        goods.setGoodsSn(PREFIX + System.nanoTime());
        goods.setName(PREFIX + "lamp");
        goods.setCategoryId(0);
        goods.setBrandId(0);
        goods.setGallery(new String[]{"http://example.invalid/a.jpg", "http://example.invalid/b.jpg"});
        goods.setKeywords("lamp,light");
        goods.setBrief("integration test");
        goods.setIsOnSale(true);
        goods.setSortOrder((short) 100);
        goods.setPicUrl("");
        goods.setShareUrl("");
        goods.setIsNew(false);
        goods.setIsHot(false);
        goods.setUnit("pcs");
        goods.setCounterPrice(new BigDecimal("10.00"));
        goods.setRetailPrice(new BigDecimal("8.50"));
        goods.setDetail("<p>rich text</p>");
        goods.setStatus("DRAFT");
        goods.setRegionId(1);
        goods.setCity("Stockholm");
        goods.setPostalCode("11122");
        goods.setLatitude(new BigDecimal("59.329323"));
        goods.setLongitude(new BigDecimal("18.068581"));
        goods.setAddTime(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        goods.setUpdateTime(goods.getAddTime());
        goods.setDeleted(false);

        StepVerifier.create(goodsMapper.insertSelective(goods)).expectNext(1).verifyComplete();
        assertNotNull(goods.getId());

        LitemallGoodsExample byId = new LitemallGoodsExample();
        byId.or().andIdEqualTo(goods.getId());
        StepVerifier.create(goodsMapper.selectOneByExampleWithBLOBs(byId))
                .assertNext(found -> {
                    assertArrayEquals(goods.getGallery(), found.getGallery());
                    assertEquals("<p>rich text</p>", found.getDetail());
                    assertEquals(0, new BigDecimal("8.50").compareTo(found.getRetailPrice()));
                    assertEquals(Boolean.TRUE, found.getIsOnSale());
                    assertEquals("DRAFT", found.getStatus());
                    assertEquals("Stockholm", found.getCity());
                    assertEquals("11122", found.getPostalCode());
                    assertEquals(0, new BigDecimal("59.329323").compareTo(found.getLatitude()));
                })
                .verifyComplete();

        LitemallGoods patch = new LitemallGoods();
        patch.setId(goods.getId());
        patch.setGallery(new String[]{"http://example.invalid/c.jpg"});
        patch.setStatus("PUBLISHED");
        StepVerifier.create(goodsMapper.updateByPrimaryKeySelective(patch)).expectNext(1).verifyComplete();
        StepVerifier.create(goodsMapper.selectByPrimaryKey(goods.getId()).map(g -> g.getGallery().length + ":" + g.getStatus() + ":" + g.getCity()))
                .expectNext("1:PUBLISHED:Stockholm").verifyComplete();

        StepVerifier.create(goodsMapper.selectByExampleSelective(byId, LitemallGoods.Column.id, LitemallGoods.Column.name))
                .assertNext(g -> {
                    assertEquals(goods.getName(), g.getName());
                    assertNull(g.getGallery());
                })
                .verifyComplete();

        StepVerifier.create(goodsMapper.logicalDeleteByPrimaryKey(goods.getId())).expectNext(1).verifyComplete();
        StepVerifier.create(goodsMapper.selectByPrimaryKeyWithLogicalDelete(goods.getId(), false)).verifyComplete();
    }
}
