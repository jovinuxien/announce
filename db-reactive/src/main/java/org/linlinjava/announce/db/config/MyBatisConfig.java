package org.linlinjava.announce.db.config;

import org.linlinjava.announce.db.r2dbc.JsonIntegerArrayR2dbcTypeHandlerAdapter;
import org.linlinjava.announce.db.r2dbc.JsonStringArrayR2dbcTypeHandlerAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pro.chenggang.project.reactive.mybatis.support.r2dbc.spring.annotation.R2dbcMapperScan;

/**
 * Persistence wiring for the reactive mappers. Import this class from any service that uses them:
 * it scans {@code org.linlinjava.announce.db.dao.reactive} and registers the JSON array column adapters.
 * The service's configuration must provide {@code spring.r2dbc.mybatis.*} (connection) and
 * {@code r2dbc.mybatis.mapper-locations=classpath:org/linlinjava/announce/db/dao/reactive/*.xml}.
 */
@Configuration
@R2dbcMapperScan("org.linlinjava.announce.db.dao.reactive")
public class MyBatisConfig {

    @Bean
    public JsonStringArrayR2dbcTypeHandlerAdapter jsonStringArrayR2dbcTypeHandlerAdapter() {
        return new JsonStringArrayR2dbcTypeHandlerAdapter();
    }

    @Bean
    public JsonIntegerArrayR2dbcTypeHandlerAdapter jsonIntegerArrayR2dbcTypeHandlerAdapter() {
        return new JsonIntegerArrayR2dbcTypeHandlerAdapter();
    }
}
