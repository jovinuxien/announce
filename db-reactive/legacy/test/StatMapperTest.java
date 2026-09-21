package org.linlinjava.announce.db;

import org.junit.jupiter.api.Test;
import org.linlinjava.announce.db.dao.StatMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.web.WebAppConfiguration;

import java.util.List;
import java.util.Map;

@WebAppConfiguration
@SpringBootTest
public class StatMapperTest {

    @Autowired
    private StatMapper statMapper;

    @Test
    public void testUser() {
        List<Map> result = statMapper.statUser();
        for (Map m : result) {
            m.forEach((k, v) -> System.out.println("key:value = " + k + ":" + v));
        }
    }

    @Test
    public void testOrder() {
        List<Map> result = statMapper.statOrder();
        for (Map m : result) {
            m.forEach((k, v) -> System.out.println("key:value = " + k + ":" + v));
        }
    }

    @Test
    public void testGoods() {
        List<Map> result = statMapper.statGoods();
        for (Map m : result) {
            m.forEach((k, v) -> System.out.println("key:value = " + k + ":" + v));
        }
    }

}
