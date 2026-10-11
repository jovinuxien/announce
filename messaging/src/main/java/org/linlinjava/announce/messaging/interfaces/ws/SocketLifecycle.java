package org.linlinjava.announce.messaging.interfaces.ws;

import org.linlinjava.announce.messaging.application.MessagingProperties;
import org.linlinjava.announce.messaging.infrastructure.live.LiveSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * What every WebSocket session of this service does besides its own frames, from {@code messaging.socket}:
 * <ul>
 *   <li>a protocol ping every {@code ping-interval}, which browsers and libraries answer by themselves;</li>
 *   <li>close 4409 when no frame at all (the pong included) arrived for {@code idle-timeout}: the peer is gone
 *       or frozen;</li>
 *   <li>close 4408 once the session's {@link LiveSink} overflowed and what it held has been delivered: the client
 *       read too slowly and catches up over REST ({@code GET .../messages?after=});</li>
 *   <li>frames the client sends are handled one at a time, text only (binary frames are ignored).</li>
 * </ul>
 * A peer whose connection stopped taking bytes altogether never receives a close frame; the server-wide write
 * timeout ({@code messaging.socket.write-timeout}, see {@code SocketConfig}) ends that connection instead.
 */
@Component
public class SocketLifecycle {

    public static final int SLOW_CONSUMER = 4408;
    public static final int IDLE = 4409;

    private static final Logger log = LoggerFactory.getLogger(SocketLifecycle.class);

    private final MessagingProperties.Socket settings;

    public SocketLifecycle(MessagingProperties properties) {
        this.settings = properties.getSocket();
    }

    /** Runs the session until the client leaves or one of the policies above closes it. */
    public Mono<Void> run(WebSocketSession session, LiveSink sink, Function<String, Mono<Void>> onText) {
        Sinks.Empty<Void> ending = Sinks.empty();          // the sink completed: the session is ending
        AtomicLong sent = new AtomicLong();
        Flux<WebSocketMessage> frames = sink.frames().map(session::textMessage).doOnNext(frame -> sent.incrementAndGet())
                .doFinally(signal -> ending.tryEmitEmpty());
        Flux<WebSocketMessage> out = frames;
        Duration ping = settings.getPingInterval();
        if (!ping.isZero()) {
            Flux<WebSocketMessage> pings = Flux.interval(ping, ping).takeUntilOther(ending.asMono())
                    .map(tick -> session.pingMessage(factory -> factory.wrap(new byte[0])));
            out = Flux.merge(frames, pings);
        }
        Mono<Void> outbound = session.send(out)
                .doOnSuccess(v -> log.debug("socket {}: send completed after {} frames, overflowed={}", session.getId(), sent.get(), sink.overflowed()))
                .then(Mono.defer(() -> sink.overflowed()
                        ? session.close(new CloseStatus(SLOW_CONSUMER, "Slow consumer: more than " + sink.capacity()
                        + " frames were waiting; reconnect and fetch the messages after the last seq you have"))
                        : Mono.empty()));

        // the idle watchdog closes the session instead of cancelling the inbound: a cancelled inbound makes Reactor
        // Netty send an empty close frame of its own, and the client would never learn the reason
        AtomicLong lastInbound = new AtomicLong(System.nanoTime());
        Mono<Void> inbound = session.receive()
                .doOnNext(message -> lastInbound.set(System.nanoTime()))
                .filter(message -> message.getType() == WebSocketMessage.Type.TEXT)
                .map(WebSocketMessage::getPayloadAsText)
                .concatMap(onText)
                .then()
                .doFinally(signal -> sink.complete());
        Duration idle = settings.getIdleTimeout();
        Mono<Void> watchdog = idle.isZero() ? Mono.empty()
                : Flux.interval(idle.dividedBy(4), idle.dividedBy(4)).takeUntilOther(ending.asMono())
                .filter(tick -> System.nanoTime() - lastInbound.get() > idle.toNanos())
                .next()
                .flatMap(tick -> session.close(new CloseStatus(IDLE, "Idle: no frame received for " + idle.toSeconds() + " s")));
        return Mono.when(outbound, inbound, watchdog);
    }

}
