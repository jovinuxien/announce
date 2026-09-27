package org.linlinjava.announce.gatewayapi;

import org.linlinjava.announce.db.config.MyBatisConfig;
import org.linlinjava.announce.gatewayapi.auth.AuthProperties;
import org.linlinjava.announce.gatewayapi.config.JwtProperties;
import org.linlinjava.announce.gatewayapi.machine.MachineTokenProperties;
import org.linlinjava.announce.gatewayapi.storage.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * The customer edge (port 8080): Spring Cloud Gateway routes of {@code config/application.yml}, the member identity
 * ({@code auth}, {@code security}), the service trust header ({@code machine}), the SPA history fallback ({@code web})
 * the picture storage ({@code storage}) and the React SPA built from {@code src/main/webapp} into {@code static/}. Design: {@code doc/frontend-edges.md}.
 */
@SpringBootApplication
@Import(MyBatisConfig.class)
@EnableConfigurationProperties({JwtProperties.class, AuthProperties.class, MachineTokenProperties.class, StorageProperties.class})
public class GatewayApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApiApplication.class, args);
    }
}
