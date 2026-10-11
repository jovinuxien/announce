package org.linlinjava.announce.cqrs.readside;

import io.r2dbc.spi.ConnectionFactory;
import org.linlinjava.announce.cqrs.CqrsProperties;
import org.linlinjava.announce.cqrs.observability.CqrsDefaultsEnvironmentPostProcessor;
import org.linlinjava.announce.cqrs.observability.CqrsTracing;
import org.linlinjava.announce.cqrs.observability.ObservabilityAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.r2dbc.core.DatabaseClient;

import java.util.logging.Logger;

/**
 * Read side: reactive projections over OpenCQRS event handling processors, the R2DBC progress tracker (with
 * partition seeding), the rebuilder, and the R2DBC lock registry for leader-elected processors.
 */
@AutoConfiguration(after = ObservabilityAutoConfiguration.class,
        afterName = "org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration",
        beforeName = "com.opencqrs.framework.eventhandler.EventHandlingProcessorAutoConfiguration")
@EnableConfigurationProperties(CqrsProperties.class)
public class ReadSideAutoConfiguration {

    private static final Logger log = Logger.getLogger(ReadSideAutoConfiguration.class.getName());

    @Bean
    public static ReactiveProjectionRegistrar reactiveProjectionRegistrar() {
        return new ReactiveProjectionRegistrar();
    }

    @Bean(name = ReactiveProjectionSupport.BEAN_NAME)
    @ConditionalOnMissingBean
    public ReactiveProjectionSupport reactiveProjectionSupport(ObjectProvider<CqrsTracing> tracing, CqrsProperties properties) {
        return new ReactiveProjectionSupport(tracing.getIfAvailable(CqrsTracing::disabled), properties.getReadside().getHandlerTimeout());
    }

    @Bean
    @ConditionalOnMissingBean
    public EventProcessors cqrsEventProcessors(ApplicationContext context) {
        return new EventProcessors(context);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProjectionRebuilder projectionRebuilder(EventProcessors processors, ObjectProvider<ReactiveProjection> projections,
                                                   ObjectProvider<ResettableProgressTracker> trackers) {
        return new ProjectionRebuilder(processors, projections.orderedStream().toList(), trackers.orderedStream().toList());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ConnectionFactory.class)
    @ConditionalOnBean(ConnectionFactory.class)
    @ConditionalOnProperty(prefix = "cqrs.readside.progress", name = "tracker", havingValue = "r2dbc", matchIfMissing = true)
    static class R2dbcProgressConfiguration {

        @Bean(name = CqrsDefaultsEnvironmentPostProcessor.R2DBC_TRACKER_BEAN)
        @ConditionalOnMissingBean(name = CqrsDefaultsEnvironmentPostProcessor.R2DBC_TRACKER_BEAN)
        public R2dbcProgressTracker r2dbcProgressTracker(ConnectionFactory connectionFactory, CqrsProperties properties,
                                                         Environment environment) {
            CqrsProperties.ReadSide.Progress settings = properties.getReadside().getProgress();
            R2dbcProgressTracker tracker = new R2dbcProgressTracker(DatabaseClient.create(connectionFactory),
                    settings.getTable(), properties.getReadside().getHandlerTimeout());
            if (settings.isCreateTable()) {
                tracker.createTableIfMissing().block();
            }
            if (settings.isSeedPartitions()) {
                ReadSidePartitions.configured(environment).forEach((group, partitions) -> {
                    Integer seeded = tracker.seedPartitions(group, partitions).block();
                    if (seeded != null && seeded > 0) {
                        log.info(() -> "Seeded " + seeded + " new partition(s) of group '" + group + "' from its lowest checkpoint ("
                                + partitions + " partitions configured)");
                    }
                });
            }
            return tracker;
        }
    }

    /**
     * {@code cqrs.readside.leader-election.enabled=true}: the lock registry OpenCQRS's leader-election
     * life-cycle controller needs ({@code opencqrs.event-handling.standard.life-cycle.controller=leader_election},
     * or just let OpenCQRS default to it, which it does when exactly one {@link LockRegistry} bean exists).
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({ConnectionFactory.class, LockRegistry.class})
    @ConditionalOnBean(ConnectionFactory.class)
    @ConditionalOnProperty(prefix = "cqrs.readside.leader-election", name = "enabled", havingValue = "true")
    static class LeaderElectionConfiguration {

        public static final String LOCK_REGISTRY_BEAN = "cqrsLockRegistry";

        @Bean(name = LOCK_REGISTRY_BEAN)
        @ConditionalOnMissingBean(LockRegistry.class)
        public R2dbcLockRegistry cqrsLockRegistry(ConnectionFactory connectionFactory, CqrsProperties properties) {
            CqrsProperties.LeaderElection settings = properties.getReadside().getLeaderElection();
            R2dbcLockRegistry registry = new R2dbcLockRegistry(DatabaseClient.create(connectionFactory), settings.getTable(),
                    settings.getTtl(), settings.getPollInterval(), properties.getReadside().getHandlerTimeout());
            if (settings.isCreateTable()) {
                registry.createTableIfMissing().block();
            }
            log.info(() -> "Leader election for event handling processors on with lock table '" + settings.getTable()
                    + "', lease " + settings.getTtl() + ", owner " + registry.owner());
            return registry;
        }
    }
}
