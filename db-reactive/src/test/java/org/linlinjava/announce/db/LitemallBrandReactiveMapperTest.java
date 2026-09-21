package org.linlinjava.announce.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.db.dao.reactive.LitemallBrandMapper;
import org.linlinjava.announce.db.domain.LitemallBrand;
import org.linlinjava.announce.db.domain.LitemallBrandExample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip test of the first reactive mapper against the local MySQL configured in application.yml.
 * Rows it creates are prefixed with {@code it-brand-} and removed afterwards.
 */
@SpringBootTest
class LitemallBrandReactiveMapperTest {

    private static final String PREFIX = "it-brand-";

    @Autowired
    private LitemallBrandMapper brandMapper;

    @AfterEach
    void cleanUp() {
        LitemallBrandExample example = new LitemallBrandExample();
        example.or().andNameLike(PREFIX + "%");
        brandMapper.deleteByExample(example).block();
    }

    @Test
    void countAndPageActiveBrands() {
        LitemallBrandExample example = new LitemallBrandExample();
        example.or().andDeletedEqualTo(false);
        example.setOrderByClause("id asc");

        Long total = brandMapper.countByExample(example).block();
        assertNotNull(total);

        StepVerifier.create(brandMapper.selectPageByExample(example, 0, 5).count())
                .assertNext(n -> assertEquals(Math.min(5, total), n))
                .verifyComplete();
    }

    @Test
    void selectiveColumnsLeaveOthersNull() {
        LitemallBrandExample example = new LitemallBrandExample();
        example.or().andDeletedEqualTo(false);

        StepVerifier.create(brandMapper.selectPageByExampleSelective(example, 0, 1, LitemallBrand.Column.id, LitemallBrand.Column.name))
                .assertNext(b -> {
                    assertNotNull(b.getId());
                    assertNotNull(b.getName());
                    assertNull(b.getPicUrl());
                    assertNull(b.getAddTime());
                })
                .verifyComplete();
    }

    @Test
    void insertGeneratesKeyThenUpdateLogicalDeleteAndHardDelete() {
        LitemallBrand brand = new LitemallBrand();
        brand.setName(PREFIX + System.nanoTime());
        brand.setDesc("integration test");
        brand.setPicUrl("http://example.invalid/pic.png");
        brand.setSortOrder((byte) 7);
        brand.setFloorPrice(new BigDecimal("12.50"));
        brand.setAddTime(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        brand.setUpdateTime(brand.getAddTime());
        brand.setDeleted(false);

        StepVerifier.create(brandMapper.insertSelective(brand)).expectNext(1).verifyComplete();
        assertNotNull(brand.getId(), "generated key must be written back");
        Integer id = brand.getId();

        StepVerifier.create(brandMapper.selectByPrimaryKey(id))
                .assertNext(found -> {
                    assertEquals(brand.getName(), found.getName());
                    assertEquals((byte) 7, found.getSortOrder());
                    assertEquals(0, new BigDecimal("12.50").compareTo(found.getFloorPrice()));
                    assertEquals(Boolean.FALSE, found.getDeleted());
                    assertEquals(brand.getAddTime(), found.getAddTime());
                })
                .verifyComplete();

        LitemallBrand patch = new LitemallBrand();
        patch.setId(id);
        patch.setDesc("updated");
        StepVerifier.create(brandMapper.updateByPrimaryKeySelective(patch)).expectNext(1).verifyComplete();
        StepVerifier.create(brandMapper.selectByPrimaryKey(id).map(LitemallBrand::getDesc))
                .expectNext("updated").verifyComplete();

        StepVerifier.create(brandMapper.logicalDeleteByPrimaryKey(id)).expectNext(1).verifyComplete();
        StepVerifier.create(brandMapper.selectByPrimaryKeyWithLogicalDelete(id, false)).verifyComplete();
        StepVerifier.create(brandMapper.selectByPrimaryKeyWithLogicalDelete(id, true))
                .assertNext(b -> assertTrue(b.getDeleted())).verifyComplete();

        StepVerifier.create(brandMapper.deleteByPrimaryKey(id)).expectNext(1).verifyComplete();
        StepVerifier.create(brandMapper.selectByPrimaryKey(id)).verifyComplete();
    }

    @Test
    void selectOneByExampleIsEmptyWhenNothingMatches() {
        LitemallBrandExample example = new LitemallBrandExample();
        example.or().andNameEqualTo(PREFIX + "does-not-exist");
        StepVerifier.create(brandMapper.selectOneByExample(example)).verifyComplete();
    }
}
