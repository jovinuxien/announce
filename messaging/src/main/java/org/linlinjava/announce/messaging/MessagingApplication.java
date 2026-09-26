package org.linlinjava.announce.messaging;

import org.linlinjava.announce.messaging.application.MessagingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * The messaging service: chat between members about an announce, member-to-staff support conversations and staff
 * bulletins. See {@code doc/messaging-context.md}.
 */
@SpringBootApplication
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessagingApplication.class, args);
    }
}
