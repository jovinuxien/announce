package org.linlinjava.announce.db;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.db.dao.LitemallSystemMapper;
import org.linlinjava.announce.db.domain.LitemallSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.web.WebAppConfiguration;

@WebAppConfiguration
@SpringBootTest
public class MapperReturnTest {

    @Autowired
    private LitemallSystemMapper systemMapper;

    @Test
    public void test() {
        LitemallSystem system = new LitemallSystem();
        system.setKeyName("test-system-key");
        system.setKeyValue("test-system-value");
        int updates = systemMapper.insertSelective(system);
        Assertions.assertEquals(updates, 1);

        updates = systemMapper.deleteByPrimaryKey(system.getId());
        Assertions.assertEquals(updates, 1);

        updates = systemMapper.updateByPrimaryKey(system);
        Assertions.assertEquals(updates, 0);
    }

}

