package org.linlinjava.announce.db;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test-only boot class. Mapper scanning (blocking and reactive) is declared in
 * {@link org.linlinjava.announce.db.config.MyBatisConfig}.
 */
@SpringBootApplication(scanBasePackages = {"org.linlinjava.announce.db"})
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
