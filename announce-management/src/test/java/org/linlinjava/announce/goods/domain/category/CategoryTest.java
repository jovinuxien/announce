package org.linlinjava.announce.goods.domain.category;

import org.junit.jupiter.api.Test;
import org.linlinjava.announce.goods.domain.announce.AnnounceRuleViolation;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 20, 12, 0);

    static Category root(int id, String name) {
        Category c = Category.root(name, "", "", 10, NOW);
        c.assignId(new CategoryId(id));
        c.pullEvents();
        return c;
    }

    @Test
    void treeHasTwoLevels() {
        Category vehicles = root(1, "Vehicles");
        Category bikes = Category.childOf(vehicles, "Bikes", "", "", 1, NOW);
        assertEquals("L2", bikes.level());
        assertEquals(vehicles.id(), bikes.parentId());
        bikes.assignId(new CategoryId(2));
        assertThrows(AnnounceRuleViolation.class, () -> Category.childOf(bikes, "Racing", "", "", 1, NOW));
    }

    @Test
    void rootsDoNotMoveAndChildrenMoveOnlyUnderRoots() {
        Category vehicles = root(1, "Vehicles");
        Category home = root(2, "Home");
        Category bikes = Category.childOf(vehicles, "Bikes", "", "", 1, NOW);
        bikes.assignId(new CategoryId(3));
        bikes.pullEvents();
        assertThrows(AnnounceRuleViolation.class, () -> vehicles.moveUnder(home, NOW));
        assertThrows(AnnounceRuleViolation.class, () -> bikes.moveUnder(bikes, NOW));
        bikes.moveUnder(home, NOW);
        assertEquals(home.id(), bikes.parentId());
        assertEquals(CategoryChanged.Kind.MOVED, bikes.pullEvents().get(0).kind());
    }

    @Test
    void hideIsIdempotentAndNamesAreRequired() {
        Category c = root(1, "Vehicles");
        c.hide(NOW);
        c.hide(NOW);
        assertTrue(c.isHidden());
        assertEquals(1, c.pullEvents().size());
        assertThrows(IllegalArgumentException.class, () -> c.rename("  ", NOW));
    }
}
