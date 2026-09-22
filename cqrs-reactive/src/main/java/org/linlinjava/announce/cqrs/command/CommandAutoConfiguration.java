package org.linlinjava.announce.cqrs.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencqrs.framework.command.CommandRouter;
import org.linlinjava.announce.cqrs.CqrsProperties;
import org.linlinjava.announce.cqrs.observability.MetaDataEnricher;
import org.linlinjava.announce.cqrs.observability.ObservabilityAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.scheduler.Schedulers;

/**
 * Command side: {@link ReactiveCommandRouter} over the OpenCQRS {@link CommandRouter}, command-id idempotency
 * and problem details.
 */
@AutoConfiguration(after = ObservabilityAutoConfiguration.class,
        afterName = {"com.opencqrs.framework.command.CommandRouterAutoConfiguration",
                "org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration",
                "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration"})
@EnableConfigurationProperties(CqrsProperties.class)
public class CommandAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ConnectionFactory.class)
    @ConditionalOnBean(ConnectionFactory.class)
    @ConditionalOnProperty(prefix = "cqrs.command.idempotency", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class R2dbcCommandLogConfiguration {

        @Bean
        @ConditionalOnMissingBean(CommandIdStore.class)
        public R2dbcCommandIdStore r2dbcCommandIdStore(ConnectionFactory connectionFactory, CqrsProperties properties) {
            CqrsProperties.Command.Idempotency settings = properties.getCommand().getIdempotency();
            R2dbcCommandIdStore store = new R2dbcCommandIdStore(DatabaseClient.create(connectionFactory), settings.getTable());
            if (settings.isCreateTable()) {
                store.createTableIfMissing().block();
            }
            return store;
        }
    }

    @Bean
    @ConditionalOnBean(CommandRouter.class)
    @ConditionalOnMissingBean
    public ReactiveCommandRouter reactiveCommandRouter(CommandRouter commandRouter,
                                                       ObjectProvider<MetaDataEnricher> enricher,
                                                       ObjectProvider<CommandIdStore> commandIdStore,
                                                       ObjectProvider<ObjectMapper> objectMapper,
                                                       CqrsProperties properties) {
        CommandIdStore store = properties.getCommand().getIdempotency().isEnabled() ? commandIdStore.getIfAvailable() : null;
        return new ReactiveCommandRouter(commandRouter, Schedulers.boundedElastic(),
                enricher.getIfAvailable(MetaDataEnricher::identity), store,
                store == null ? null : objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public CqrsProblemDetailsHandler cqrsProblemDetailsHandler() {
        return new CqrsProblemDetailsHandler();
    }
}
