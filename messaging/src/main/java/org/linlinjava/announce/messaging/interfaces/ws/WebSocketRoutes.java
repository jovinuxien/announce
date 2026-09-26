package org.linlinjava.announce.messaging.interfaces.ws;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;

import java.util.Map;

/** Maps the two WebSocket endpoints; the {@code WebSocketHandlerAdapter} comes from Spring Boot's WebFlux setup. */
@Configuration(proxyBeanMethods = false)
public class WebSocketRoutes {

    public static final String CONVERSATIONS = "/ws/conversations/*";
    public static final String STAFF = "/ws/staff";

    @Bean
    HandlerMapping messagingWebSocketMapping(ConversationWebSocketHandler conversations, StaffWebSocketHandler staff) {
        return new SimpleUrlHandlerMapping(Map.of(CONVERSATIONS, conversations, STAFF, staff), -1);
    }
}
