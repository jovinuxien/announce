package org.linlinjava.announce.messaging.load;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tuning step 5 of {@code doc/messaging-architecture.md} against a running node: staff sockets whose client never
 * reads (and never answers a ping), one that does, and a burst of bulletins over REST that every staff socket
 * receives. Prints every two seconds what the node reports about its sockets: sessions, frames queued, frames
 * refused, heap, until the stalled sockets are gone or the watch time is over.
 *
 * <pre>
 * mvn -pl messaging -Dmaven.test.skip=true test-compile exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=org.linlinjava.announce.messaging.load.StalledSocketDriver \
 *     -Dstall.base=http://localhost:8098 -Dstall.stalled=10 -Dstall.bulletins=10000 -Dstall.concurrency=16 -Dstall.watch=90
 * </pre>
 * The bulletins stay in {@code announce_bulletin}; remove them with {@code delete from announce_bulletin where
 * content like 'load %'}.
 */
public final class StalledSocketDriver {

    static final Pattern SAMPLE = Pattern.compile("^(\\S+?)(\\{[^}]*\\})? (\\S+)$");

    public static void main(String[] args) throws Exception {
        String base = System.getProperty("stall.base", "http://localhost:8098");
        int stalled = Integer.getInteger("stall.stalled", 10);
        int bulletins = Integer.getInteger("stall.bulletins", 5000);
        int concurrency = Integer.getInteger("stall.concurrency", 16);
        int contentChars = Integer.getInteger("stall.content", 1000);
        int watchSeconds = Integer.getInteger("stall.watch", 90);
        WebClient http = WebClient.builder().baseUrl(base).codecs(c -> c.defaultCodecs().maxInMemorySize(64 << 20)).build();
        System.out.printf("base=%s stalled=%d bulletins=%d concurrency=%d content=%d chars watch=%ds%n",
                base, stalled, bulletins, concurrency, contentChars, watchSeconds);

        System.out.println("before:    " + snapshot(http));
        List<Disposable> connections = new ArrayList<>();
        AtomicLong readerFrames = new AtomicLong();
        connections.add(socket(base, 9_900, true, readerFrames));
        for (int i = 0; i < stalled; i++) {
            connections.add(socket(base, 9_901 + i, false, readerFrames));
        }
        Thread.sleep(1_000);
        System.out.println("connected: " + snapshot(http));

        String content = "load stall " + "b".repeat(Math.max(0, contentChars - 11));
        long t0 = System.nanoTime();
        AtomicLong posted = new AtomicLong();
        AtomicLong failed = new AtomicLong();
        Flux.range(0, bulletins)
                .flatMap(i -> http.post().uri("/api/admin/bulletins").headers(h -> { h.set("X-User-Id", "9900"); h.set("X-User-Type", "ADMIN"); })
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("audience", "STAFF", "content", content))
                        .retrieve().toBodilessEntity().timeout(Duration.ofSeconds(30))
                        .doOnNext(r -> posted.incrementAndGet())
                        .onErrorResume(e -> {
                            if (failed.incrementAndGet() <= 3) {
                                System.err.println("bulletin post failed: " + e);
                            }
                            return Mono.empty();
                        }), concurrency)
                .blockLast(Duration.ofMinutes(10));
        System.out.printf("posted %d bulletins (%d failed) in %.1f s%n", posted.get(), failed.get(), (System.nanoTime() - t0) / 1e9);

        long watchEnd = System.nanoTime() + TimeUnit.SECONDS.toNanos(watchSeconds);
        while (System.nanoTime() < watchEnd) {
            Map<String, Double> m = scrape(http);
            System.out.printf("t+%3.0fs %s reader_frames=%d%n", (System.nanoTime() - t0) / 1e9, format(m), readerFrames.get());
            if (m.getOrDefault("messaging_staff_sessions", 0d) <= 1) {
                System.out.println("stalled sockets reclaimed");
                break;
            }
            Thread.sleep(2_000);
        }
        connections.forEach(Disposable::dispose);
        Thread.sleep(1_000);
        System.out.println("after:     " + snapshot(http));
        System.exit(0);
    }

    static Disposable socket(String base, int staffId, boolean reads, AtomicLong readerFrames) throws InterruptedException {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-User-Id", String.valueOf(staffId));
        headers.add("X-User-Type", "ADMIN");
        CountDownLatch open = new CountDownLatch(1);
        String ws = base.replaceFirst("^http", "ws") + "/ws/staff?clientType=desktop";
        ReactorNettyWebSocketClient client = reads ? new ReactorNettyWebSocketClient()
                : new ReactorNettyWebSocketClient(reactor.netty.http.client.HttpClient.create(),
                () -> reactor.netty.http.client.WebsocketClientSpec.builder().handlePing(true));
        Disposable connection = client.execute(URI.create(ws), headers, session -> {
            open.countDown();
            if (!reads) {
                return Mono.never();                      // never subscribes to the inbound: the connection stops reading
            }
            return session.receive().filter(m -> m.getType() == WebSocketMessage.Type.TEXT)
                    .doOnNext(m -> readerFrames.incrementAndGet()).then();
        }).subscribe(v -> {
        }, e -> System.err.println("socket " + staffId + ": " + e));
        if (!open.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("socket did not open: " + ws);
        }
        return connection;
    }

    static String snapshot(WebClient http) {
        return format(scrape(http));
    }

    static String format(Map<String, Double> m) {
        return String.format("staff_sessions=%.0f buffered=%.0f buffered_max=%.0f dropped=%.0f delivered_bulletin=%.0f heap_used=%.1fMB",
                m.getOrDefault("messaging_staff_sessions", 0d), m.getOrDefault("messaging_sink_buffered", 0d),
                m.getOrDefault("messaging_sink_buffered_max", 0d), m.getOrDefault("messaging_sink_dropped_total", 0d),
                m.getOrDefault("messaging_live_delivered_total{record=\"bulletin\"}", 0d), m.getOrDefault("heap", 0d) / (1 << 20));
    }

    /** The gauges and counters of interest; counters with several tag sets are summed, heap over its memory pools. */
    static Map<String, Double> scrape(WebClient http) {
        String text = http.get().uri("/actuator/prometheus").retrieve().bodyToMono(String.class).block(Duration.ofSeconds(10));
        Map<String, Double> values = new java.util.HashMap<>();
        for (String line : text.split("\n")) {
            if (line.startsWith("#")) {
                continue;
            }
            Matcher match = SAMPLE.matcher(line.trim());
            if (!match.matches()) {
                continue;
            }
            String name = match.group(1);
            String tags = match.group(2) == null ? "" : match.group(2);
            double value = Double.parseDouble(match.group(3));
            if (name.startsWith("messaging_staff_sessions") || name.startsWith("messaging_sink_buffered")) {
                values.merge(name, value, Double::sum);
            } else if (name.equals("messaging_sink_dropped_total")) {
                values.merge(name, value, Double::sum);
            } else if (name.equals("messaging_live_delivered_total") && tags.contains("record=\"bulletin\"")) {
                values.merge(name + "{record=\"bulletin\"}", value, Double::sum);
            } else if (name.equals("jvm_memory_used_bytes") && tags.contains("area=\"heap\"")) {
                values.merge("heap", value, Double::sum);
            }
        }
        return values;
    }
}
