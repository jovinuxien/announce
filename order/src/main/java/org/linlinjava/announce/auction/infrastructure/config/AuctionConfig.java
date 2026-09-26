package org.linlinjava.announce.auction.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AuctionProperties.class)
public class AuctionConfig {
}
