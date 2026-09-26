package org.linlinjava.announce.order.infrastructure.config;

import org.linlinjava.announce.order.domain.OrderPolicies;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OrderProperties.class)
public class OrderConfig {

    /** Validated once at start-up: a window outside the allowed range fails the boot. */
    @Bean
    OrderPolicies orderPolicies(OrderProperties properties) {
        return properties.toPolicies();
    }
}
