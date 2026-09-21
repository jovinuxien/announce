package org.linlinjava.announce.db;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.web.WebAppConfiguration;

@WebAppConfiguration
@SpringBootTest
public class DbConfigTest {
    @Autowired
    Environment environment;

    @Test
    public void test() {
        System.out.println(environment.getProperty("spring.datasource.druid.url"));
    }
}
