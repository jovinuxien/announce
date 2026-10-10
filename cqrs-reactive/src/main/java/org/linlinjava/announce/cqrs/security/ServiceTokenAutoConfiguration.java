package org.linlinjava.announce.cqrs.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

import java.time.Clock;

/**
 * Edge trust: the inbound {@link ServiceTokenWebFilter} ({@code announce.service-token.mode}) and the
 * {@link ServiceTokenMinter} for outbound service-to-service calls. Both use the wall clock, not an application
 * {@link Clock} bean, because freshness is about real time between hosts.
 */
@AutoConfiguration
@EnableConfigurationProperties(ServiceTokenProperties.class)
public class ServiceTokenAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ServiceTokenMinter serviceTokenMinter(ServiceTokenProperties properties, Environment environment) {
        String service = properties.getService() != null && !properties.getService().isBlank()
                ? properties.getService() : environment.getProperty("spring.application.name", "service");
        return new ServiceTokenMinter(service, properties.getSecret(), Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @Order(Integer.MIN_VALUE + 200)
    public ServiceTokenWebFilter serviceTokenWebFilter(ServiceTokenProperties properties) {
        return new ServiceTokenWebFilter(properties, Clock.systemUTC());
    }
}
