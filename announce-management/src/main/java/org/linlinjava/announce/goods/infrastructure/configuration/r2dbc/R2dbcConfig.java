package org.linlinjava.announce.goods.infrastructure.configuration.r2dbc;

import org.linlinjava.announce.db.config.MyBatisConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Persistence wiring for announce-management.
 *
 * <p>The connection factory and the reactive transaction manager come from reactive-mybatis-support
 * ({@code spring.r2dbc.mybatis.*} in application.yml); {@link MyBatisConfig} registers the announce_* mappers
 * from the db-reactive module. The Spring Data R2DBC repositories ({@code Announce*Entity}) keep working on
 * the same connection factory.
 */
@Configuration
@EnableTransactionManagement
@EnableR2dbcRepositories(basePackages = "org.linlinjava.announce.goods.domain.model.repositories")
@EnableR2dbcAuditing
@Import(MyBatisConfig.class)
public class R2dbcConfig {
}
