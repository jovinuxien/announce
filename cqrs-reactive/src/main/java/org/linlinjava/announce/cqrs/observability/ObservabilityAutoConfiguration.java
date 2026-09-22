package org.linlinjava.announce.cqrs.observability;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.linlinjava.announce.cqrs.CqrsProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

/**
 * Correlation and causation ids in event metadata and Kafka headers, wired to Micrometer tracing when a
 * {@link Tracer} is present.
 */
@AutoConfiguration
@EnableConfigurationProperties(CqrsProperties.class)
public class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MetaDataEnricher cqrsMetaDataEnricher(ObjectProvider<Tracer> tracer) {
        return new TracingMetaDataEnricher(tracer.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public CqrsTracing cqrsTracing(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        return new CqrsTracing(tracer.getIfAvailable(), propagator.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @Order(Integer.MIN_VALUE + 100)
    public CorrelationWebFilter correlationWebFilter(CqrsProperties properties) {
        return new CorrelationWebFilter(properties.getObservability().getCorrelationHeader());
    }
}
