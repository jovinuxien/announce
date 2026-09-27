package org.linlinjava.announce.cqrs.command;

import com.opencqrs.framework.types.EventTypeResolver;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Replaces OpenCQRS's strict class-name {@link EventTypeResolver} by {@link TolerantEventTypeResolver}, so a
 * processor whose fetch subject covers another service's events skips them instead of stopping. Runs before
 * OpenCQRS's own auto-configuration, whose bean is conditional on a missing {@link EventTypeResolver}.
 * Switch off with {@code cqrs.command.tolerant-event-types=false} to get the strict resolver back.
 */
@AutoConfiguration(beforeName = "com.opencqrs.framework.types.ClassNameEventTypeResolverAutoConfiguration")
@ConditionalOnClass(EventTypeResolver.class)
@ConditionalOnProperty(prefix = "cqrs.command", name = "tolerant-event-types", havingValue = "true", matchIfMissing = true)
public class EventTypeAutoConfiguration implements BeanClassLoaderAware {

    private ClassLoader classLoader;

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Bean
    @ConditionalOnMissingBean(EventTypeResolver.class)
    public TolerantEventTypeResolver tolerantEventTypeResolver() {
        return new TolerantEventTypeResolver(classLoader == null ? getClass().getClassLoader() : classLoader);
    }
}
