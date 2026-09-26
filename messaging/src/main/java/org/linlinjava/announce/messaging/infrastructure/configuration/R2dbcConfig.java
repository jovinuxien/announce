package org.linlinjava.announce.messaging.infrastructure.configuration;

import org.linlinjava.announce.db.config.MyBatisConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Persistence wiring: the connection factory and the reactive transaction manager come from
 * reactive-mybatis-support ({@code spring.r2dbc.mybatis.*}); {@link MyBatisConfig} registers the announce_* mappers
 * of db-reactive. The cqrs-reactive framework shares the same connection factory for its command log and
 * read-side checkpoints.
 */
@Configuration
@EnableTransactionManagement
@Import(MyBatisConfig.class)
public class R2dbcConfig {
}
