package org.linlinjava.announce.db;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs every generated reactive mapper against the seeded database: parses its XML, counts the table,
 * pages one row with the full result map (so every column type and JSON array adapter is exercised),
 * and reads the first row back by primary key. Catches mapping regressions across all 25 tables at once.
 */
@SpringBootTest
class AllReactiveMappersSmokeTest {

    @Autowired
    private ApplicationContext context;

    @TestFactory
    List<DynamicTest> everyGeneratedMapperCountsPagesAndReadsByKey() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String beanName : context.getBeanNamesForType(Object.class)) {
            Object bean = context.getBean(beanName);
            Class<?> mapper = mapperInterface(bean);
            if (mapper == null) continue;
            Method count = find(mapper, "countByExample");
            if (count == null) continue; // hand-written mappers are covered by CustomReactiveMappersTest
            tests.add(DynamicTest.dynamicTest(mapper.getSimpleName(), () -> {
                Object example = count.getParameterTypes()[0].getConstructor().newInstance();
                Long total = ((Mono<Long>) count.invoke(bean, example)).block();
                assertNotNull(total, "count must return a value");

                Method page = find(mapper, "selectPageByExample");
                Object first = ((Flux<?>) page.invoke(bean, example, 0L, 1)).blockFirst();
                if (total > 0) {
                    assertNotNull(first, "table has rows but the page was empty");
                    Method key = primaryKeyGetter(first.getClass());
                    Object id = key.invoke(first);
                    Method byKey = find(mapper, "selectByPrimaryKey");
                    Object again = ((Mono<?>) byKey.invoke(bean, id)).block();
                    assertNotNull(again, "row " + id + " must be readable by primary key");
                    assertTrue(key.invoke(again).equals(id));
                }
            }));
        }
        assertTrue(tests.size() >= 25, "expected at least 25 generated mappers, found " + tests.size());
        return tests;
    }

    private static Class<?> mapperInterface(Object bean) {
        for (Class<?> itf : bean.getClass().getInterfaces()) {
            if (itf.getName().startsWith("org.linlinjava.announce.db.dao.reactive.")) return itf;
        }
        return null;
    }

    /** Generated rows expose {@code getId()}; the per-kind detail tables of the announce read model are keyed by {@code announce_id}. */
    private static Method primaryKeyGetter(Class<?> row) throws NoSuchMethodException {
        Method id = find(row, "getId");
        return id != null ? id : row.getMethod("getAnnounceId");
    }

    private static Method find(Class<?> type, String name) {
        for (Method m : type.getMethods()) if (m.getName().equals(name)) return m;
        return null;
    }
}
