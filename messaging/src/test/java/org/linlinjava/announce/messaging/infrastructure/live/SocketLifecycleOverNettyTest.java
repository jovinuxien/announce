package org.linlinjava.announce.messaging.infrastructure.live;

import org.junit.jupiter.api.Test;
import org.linlinjava.announce.messaging.application.MessagingProperties;
import org.linlinjava.announce.messaging.interfaces.ws.SocketLifecycle;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.adapter.ReactorNettyWebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.websocket.WebsocketInbound;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** The real SocketLifecycle over Spring's Reactor Netty session, against a client that reads nothing for a while. */
class SocketLifecycleOverNettyTest {

    @Test
    void whatTheSinkAcceptsIsSentWhenTheClientReadsAgain() throws Exception {
        reactor.core.publisher.Hooks.enableAutomaticContextPropagation();   // as Spring Boot does with context-propagation on the classpath
        MessagingProperties properties = new MessagingProperties();
        properties.getSocket().setPingInterval(Duration.ofSeconds(1));
        properties.getSocket().setIdleTimeout(Duration.ofSeconds(30));
        properties.getSocket().setBufferFrames(64);
        SocketLifecycle lifecycle = new SocketLifecycle(properties);
        AtomicInteger refused = new AtomicInteger();
        AtomicReference<LiveSink> sinkRef = new AtomicReference<>();
        Sinks.One<Boolean> handlerDone = Sinks.one();
        CountDownLatch attached = new CountDownLatch(1);
        DisposableServer server = HttpServer.create().port(0)
                .doOnConnection(c -> c.addHandlerLast("messagingWriteTimeout", new io.netty.handler.timeout.WriteTimeoutHandler(10_000, TimeUnit.MILLISECONDS)))
                .route(routes -> routes.ws("/ws", (in, out) -> {
            LiveSink sink = new LiveSink(64, result -> refused.incrementAndGet());
            sinkRef.set(sink);
            HandshakeInfo info = new HandshakeInfo(URI.create("ws://localhost/ws"), new HttpHeaders(), Mono.empty(), null);
            ReactorNettyWebSocketSession session = new ReactorNettyWebSocketSession(in, out, info, new NettyDataBufferFactory(io.netty.buffer.ByteBufAllocator.DEFAULT));
            attached.countDown();
            return lifecycle.run(session, sink, text -> Mono.empty()).doOnTerminate(() -> handlerDone.tryEmitValue(true));
        })).bindNow();

        CountDownLatch open = new CountDownLatch(1);
        Sinks.Empty<Void> readGate = Sinks.empty();
        AtomicInteger received = new AtomicInteger();
        Sinks.One<Object> closed = Sinks.one();
        HttpClient.create().websocket().uri("ws://localhost:" + server.port() + "/ws").handle((in, out) -> {
            open.countDown();
            WebsocketInbound wsIn = (WebsocketInbound) in;
            wsIn.receiveCloseStatus().subscribe(closed::tryEmitValue);
            return readGate.asMono().thenMany(Flux.defer(wsIn::receiveFrames))
                    .doOnNext(f -> { if (f instanceof io.netty.handler.codec.http.websocketx.TextWebSocketFrame) received.incrementAndGet(); }).then();
        }).subscribe();
        assertThat(open.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(attached.await(10, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(300);

        String frame = "x".repeat(4000);
        for (int i = 0; i < 4000; i++) {
            sinkRef.get().emit(frame);
        }
        int accepted = 4000 - refused.get();
        readGate.tryEmitEmpty();
        Object status = closed.asMono().block(Duration.ofSeconds(20));
        handlerDone.asMono().block(Duration.ofSeconds(20));
        Thread.sleep(300);
        System.out.printf("accepted=%d received=%d close=%s%n", accepted, received.get(), status);
        server.disposeNow();
        assertThat(received.get()).as("received by the client").isEqualTo(accepted);
        assertThat(status.toString()).contains("4408");
    }
}
