package org.linlinjava.announce.messaging.infrastructure.live;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.websocket.WebsocketInbound;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** The sink, merged with pings, over a real Reactor Netty socket whose client reads nothing for a while. */
class LiveSinkOverNettyTest {

    @Test
    void whatTheSinkAcceptsReachesTheSendWhenTheClientReadsAgain() throws Exception {
        for (boolean withTimeout : new boolean[]{false, true}) {
            boolean withPings = true;
            AtomicInteger refused = new AtomicInteger();
            AtomicInteger sent = new AtomicInteger();
            AtomicReference<LiveSink> sinkRef = new AtomicReference<>();
            Sinks.One<Integer> sendDone = Sinks.one();
            CountDownLatch attached = new CountDownLatch(1);
            HttpServer httpServer = HttpServer.create().port(0);
            if (withTimeout) {
                httpServer = httpServer.doOnConnection(c -> c.addHandlerLast("wt", new io.netty.handler.timeout.WriteTimeoutHandler(30)));
            }
            DisposableServer server = httpServer.route(routes -> routes.ws("/ws", (in, out) -> {
                LiveSink sink = new LiveSink(64, result -> refused.incrementAndGet());
                sinkRef.set(sink);
                Sinks.Empty<Void> ending = Sinks.empty();
                Flux<String> frames = sink.frames().doOnNext(f -> sent.incrementAndGet()).doFinally(s -> ending.tryEmitEmpty());
                Flux<String> outFlux = withPings
                        ? Flux.merge(frames, Flux.interval(Duration.ofMillis(200)).map(t -> "ping").takeUntilOther(ending.asMono()))
                        : frames;
                attached.countDown();
                return out.sendString(outFlux).then().doOnSuccess(v -> sendDone.tryEmitValue(sent.get()));
            })).bindNow();

            CountDownLatch open = new CountDownLatch(1);
            Sinks.Empty<Void> readGate = Sinks.empty();
            AtomicInteger received = new AtomicInteger();
            HttpClient.create().websocket().uri("ws://localhost:" + server.port() + "/ws").handle((in, out) -> {
                open.countDown();
                return readGate.asMono().thenMany(Flux.defer(() -> ((WebsocketInbound) in).receiveFrames())).doOnNext(f -> received.incrementAndGet()).then();
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
            Integer sentAtCompletion = sendDone.asMono().block(Duration.ofSeconds(20));
            Thread.sleep(300);
            System.out.printf("writeTimeout=%s accepted=%d sent=%d (at send completion %d) received=%d%n", withTimeout, accepted, sent.get(), sentAtCompletion, received.get());
            server.disposeNow();
            assertThat(sent.get()).as("emitted by the sink, writeTimeout=" + withTimeout).isEqualTo(accepted);
            assertThat(received.get()).as("received by the client, writeTimeout=" + withTimeout).isGreaterThanOrEqualTo(accepted);
        }
    }
}
