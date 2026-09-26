package org.linlinjava.announce.gatewayapi.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** One injectable clock so tests can move time (token expiry). */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }
}
