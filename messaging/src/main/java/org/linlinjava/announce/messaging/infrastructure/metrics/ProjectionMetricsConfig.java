package org.linlinjava.announce.messaging.infrastructure.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.linlinjava.announce.cqrs.readside.ReactiveProjection;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Times every projection group of this service (the read model and the two relays) by wrapping each
 * {@link ReactiveProjection} bean in a {@link MeteredProjection} before the framework registers it with
 * OpenCQRS. Static bean: a {@link BeanPostProcessor} must exist before the beans it wraps.
 */
@Configuration(proxyBeanMethods = false)
public class ProjectionMetricsConfig {

    @Bean
    static BeanPostProcessor projectionMetricsPostProcessor(BeanFactory beanFactory) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof ReactiveProjection projection && !(bean instanceof MeteredProjection)) {
                    return new MeteredProjection(projection,
                            () -> beanFactory.getBeanProvider(MeterRegistry.class).getIfAvailable(), Clock.systemUTC());
                }
                return bean;
            }
        };
    }
}
