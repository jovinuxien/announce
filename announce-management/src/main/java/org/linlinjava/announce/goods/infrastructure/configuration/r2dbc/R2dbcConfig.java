package org.linlinjava.announce.goods.infrastructure.configuration.r2dbc;

import org.linlinjava.announce.db.config.MyBatisConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Persistence wiring for announce-management.
 *
 * <p>The connection factory and the reactive transaction manager come from reactive-mybatis-support
 * ({@code spring.r2dbc.mybatis.*} in application.yml); {@link MyBatisConfig} registers the announce_* mappers
 * from the db-reactive module. The cqrs-reactive framework shares the same connection factory for its
 * command log and read-side checkpoints.
 */
@Configuration
@EnableTransactionManagement
@Import(MyBatisConfig.class)
public class R2dbcConfig {
}
