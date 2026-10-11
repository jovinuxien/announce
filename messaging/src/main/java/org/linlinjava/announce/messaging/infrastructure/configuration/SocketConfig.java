package org.linlinjava.announce.messaging.infrastructure.configuration;

import io.netty.handler.timeout.WriteTimeoutHandler;
import org.linlinjava.announce.messaging.application.MessagingProperties;
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * A write the kernel does not take within {@code messaging.socket.write-timeout} ends the connection
 * (Netty's {@link WriteTimeoutHandler} on every connection of the server). A WebSocket peer that stopped reading
 * has a full receive window, so the server's writes to it never complete: the sink behind it stops growing at
 * its bound, but only this timeout frees the session, because a close frame could not be delivered either.
 */
@Configuration(proxyBeanMethods = false)
public class SocketConfig {

    @Bean
    NettyServerCustomizer socketWriteTimeout(MessagingProperties properties) {
        Duration timeout = properties.getSocket().getWriteTimeout();
        if (timeout.isZero()) {
            return server -> server;
        }
        return server -> server.doOnConnection(connection ->
                connection.addHandlerLast("messagingWriteTimeout", new WriteTimeoutHandler(timeout.toMillis(), TimeUnit.MILLISECONDS)));
    }
}
