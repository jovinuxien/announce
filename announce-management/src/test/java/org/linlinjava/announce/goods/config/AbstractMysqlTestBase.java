package org.linlinjava.announce.goods.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.util.Collections;

public class AbstractMysqlTestBase implements  SqlTestContainer{


    private static final Logger log = LoggerFactory.getLogger(AbstractMysqlTestBase.class);

/*
    protected static final GenericContainer<?> MY_SQL_CONTAINER;
*/
    private MySQLContainer<?> mySQLContainer;

    @Override
    public void destroy() {
        if (null != mySQLContainer && mySQLContainer.isRunning()) {
            mySQLContainer.stop();
        }
    }

    @Override
    public void afterPropertiesSet() {
        if (null == mySQLContainer) {
            mySQLContainer =
                    new MySQLContainer<>("mysql:8.0.36")
                            .withDatabaseName("litemall")
                            .withCommand("--innodb-use-native-aio=0") // Disable AIO
                            .withTmpFs(Collections.singletonMap("/testtmpfs", "rw"))
                            .withLogConsumer(new Slf4jLogConsumer(log))
                            .withReuse(true);
        }
        if (!mySQLContainer.isRunning()) {
            mySQLContainer.start();
        }
    }

    @Override
    public JdbcDatabaseContainer<?> getTestContainer() {
        return mySQLContainer;
    }
}
