package org.linlinjava.announce.cqrs.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.announce.cqrs.CqrsProperties;
import org.linlinjava.announce.cqrs.observability.CqrsTracing;
import org.linlinjava.announce.cqrs.observability.ObservabilityAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Messaging: relays from internal events to public Kafka topics and subscribers of other services' topics.
 */
@AutoConfiguration(after = ObservabilityAutoConfiguration.class,
        afterName = {"org.springframework.cloud.stream.function.FunctionConfiguration",
                "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration"})
@ConditionalOnClass(StreamBridge.class)
@EnableConfigurationProperties(CqrsProperties.class)
public class MessagingAutoConfiguration {

    @Bean
    @ConditionalOnBean(StreamBridge.class)
    @ConditionalOnMissingBean
    public PublicEventRelays publicEventRelays(StreamBridge streamBridge, ObjectProvider<ObjectMapper> objectMapper,
                                               ObjectProvider<CqrsTracing> tracing, Environment environment) {
        return new PublicEventRelays(streamBridge, objectMapper.getIfAvailable(ObjectMapper::new),
                tracing.getIfAvailable(CqrsTracing::disabled), environment.getProperty("spring.application.name"));
    }

    @Bean
    @ConditionalOnMissingBean
    public PublicEventSubscribers publicEventSubscribers(ObjectProvider<ObjectMapper> objectMapper,
                                                         ObjectProvider<CqrsTracing> tracing, CqrsProperties properties) {
        return new PublicEventSubscribers(objectMapper.getIfAvailable(ObjectMapper::new),
                tracing.getIfAvailable(CqrsTracing::disabled), properties.getMessaging().getHandlerTimeout());
    }
}
