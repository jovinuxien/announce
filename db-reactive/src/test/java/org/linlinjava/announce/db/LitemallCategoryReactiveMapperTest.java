package org.linlinjava.announce.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.db.dao.reactive.LitemallCategoryMapper;
import org.linlinjava.announce.db.domain.LitemallCategory;
import org.linlinjava.announce.db.domain.LitemallCategoryExample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip test of the reactive category mapper against the local MySQL configured in application.yml.
 * Rows it creates are prefixed with {@code it-cat-} and removed afterwards.
 */
@SpringBootTest
class LitemallCategoryReactiveMapperTest {

    private static final String PREFIX = "it-cat-";

    @Autowired
    private LitemallCategoryMapper categoryMapper;

    @AfterEach
    void cleanUp() {
        LitemallCategoryExample example = new LitemallCategoryExample();
        example.or().andNameLike(PREFIX + "%");
        categoryMapper.deleteByExample(example).block();
    }

    @Test
    void level1CategoriesArePagedAndHaveNoParent() {
        LitemallCategoryExample example = new LitemallCategoryExample();
        example.or().andLevelEqualTo("L1").andDeletedEqualTo(false);
        example.setOrderByClause("sort_order asc, id asc");

        Long total = categoryMapper.countByExample(example).block();
        assertNotNull(total);

        StepVerifier.create(categoryMapper.selectPageByExample(example, 0, 3).collectList())
                .assertNext(page -> {
                    assertEquals(Math.min(3, total), page.size());
                    page.forEach(c -> assertEquals(0, c.getPid()));
                })
                .verifyComplete();
    }

    @Test
    void selectiveColumnsLeaveOthersNull() {
        LitemallCategoryExample example = new LitemallCategoryExample();
        example.or().andDeletedEqualTo(false);

        StepVerifier.create(categoryMapper.selectPageByExampleSelective(example, 0, 1,
                        LitemallCategory.Column.id, LitemallCategory.Column.name, LitemallCategory.Column.iconUrl))
                .assertNext(c -> {
                    assertNotNull(c.getId());
                    assertNotNull(c.getName());
                    assertNull(c.getLevel());
                    assertNull(c.getAddTime());
                })
                .verifyComplete();
    }

    @Test
    void parentChildRoundTripWithGeneratedKeys() {
        LitemallCategory parent = newCategory("L1", 0);
        StepVerifier.create(categoryMapper.insertSelective(parent)).expectNext(1).verifyComplete();
        assertNotNull(parent.getId(), "generated key must be written back");

        LitemallCategory child = newCategory("L2", parent.getId());
        StepVerifier.create(categoryMapper.insert(child)).expectNext(1).verifyComplete();
        assertNotNull(child.getId());

        LitemallCategoryExample byPid = new LitemallCategoryExample();
        byPid.or().andPidEqualTo(parent.getId()).andDeletedEqualTo(false);
        StepVerifier.create(categoryMapper.selectByExample(byPid))
                .assertNext(c -> {
                    assertEquals(child.getId(), c.getId());
                    assertEquals("L2", c.getLevel());
                    assertEquals(child.getName(), c.getName());
                    assertEquals(child.getAddTime(), c.getAddTime());
                })
                .verifyComplete();

        LitemallCategoryExample l2ByIds = new LitemallCategoryExample();
        l2ByIds.or().andIdIn(List.of(parent.getId(), child.getId())).andLevelEqualTo("L2").andDeletedEqualTo(false);
        StepVerifier.create(categoryMapper.countByExample(l2ByIds)).expectNext(1L).verifyComplete();

        LitemallCategory patch = new LitemallCategory();
        patch.setKind("GOODS");
        patch.setId(child.getId());
        patch.setDesc("updated");
        StepVerifier.create(categoryMapper.updateByPrimaryKeySelective(patch)).expectNext(1).verifyComplete();
        StepVerifier.create(categoryMapper.selectByPrimaryKey(child.getId()).map(LitemallCategory::getDesc))
                .expectNext("updated").verifyComplete();

        StepVerifier.create(categoryMapper.logicalDeleteByExample(byPid)).expectNext(1).verifyComplete();
        StepVerifier.create(categoryMapper.selectByPrimaryKeyWithLogicalDelete(child.getId(), true))
                .assertNext(c -> assertTrue(c.getDeleted())).verifyComplete();
        StepVerifier.create(categoryMapper.selectByExample(byPid)).verifyComplete();

        StepVerifier.create(categoryMapper.deleteByPrimaryKey(child.getId())).expectNext(1).verifyComplete();
        StepVerifier.create(categoryMapper.deleteByPrimaryKey(parent.getId())).expectNext(1).verifyComplete();
        StepVerifier.create(categoryMapper.selectByPrimaryKey(parent.getId())).verifyComplete();
    }

    @Test
    void selectOneByExampleIsEmptyWhenNothingMatches() {
        LitemallCategoryExample example = new LitemallCategoryExample();
        example.or().andNameEqualTo(PREFIX + "does-not-exist");
        StepVerifier.create(categoryMapper.selectOneByExample(example)).verifyComplete();
    }

    private static LitemallCategory newCategory(String level, Integer pid) {
        LitemallCategory c = new LitemallCategory();
        c.setKind("GOODS");
        c.setName(PREFIX + level + "-" + System.nanoTime());
        c.setKeywords("[]");
        c.setDesc("integration test");
        c.setPid(pid);
        c.setIconUrl("");
        c.setPicUrl("");
        c.setLevel(level);
        c.setSortOrder((byte) 50);
        c.setAddTime(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        c.setUpdateTime(c.getAddTime());
        c.setDeleted(false);
        return c;
    }
}
