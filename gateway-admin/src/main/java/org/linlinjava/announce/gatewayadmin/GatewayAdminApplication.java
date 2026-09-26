package org.linlinjava.announce.gatewayadmin;

import org.linlinjava.announce.db.config.MyBatisConfig;
import org.linlinjava.announce.gatewayadmin.config.JwtProperties;
import org.linlinjava.announce.gatewayadmin.config.MachineTokenProperties;
import org.linlinjava.announce.gatewayadmin.config.RefreshProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/**
 * The staff edge: Spring Cloud Gateway routes to the services, a local {@code /auth} controller over
 * {@code announce_admin}, edge-hosted admin CRUD over the db-reactive mappers, and the React admin SPA served from
 * {@code classpath:/static}. Design: {@code doc/frontend-edges.md}.
 */
@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, RefreshProperties.class, MachineTokenProperties.class})
@Import(MyBatisConfig.class)
public class GatewayAdminApplication {

    private static final Logger log = LoggerFactory.getLogger(GatewayAdminApplication.class);

    public static void main(String[] args) {
        Environment env = SpringApplication.run(GatewayAdminApplication.class, args).getEnvironment();
        log.info("\n----------------------------------------------------------\n\t"
                        + "Admin edge '{}' is running on http://localhost:{}  profiles: {}\n"
                        + "----------------------------------------------------------",
                env.getProperty("spring.application.name"), env.getProperty("server.port"), String.join(",", env.getActiveProfiles()));
    }
}
