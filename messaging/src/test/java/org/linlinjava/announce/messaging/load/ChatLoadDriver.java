package org.linlinjava.announce.messaging.load;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The load shape of {@code doc/messaging-architecture.md} section 5 against a running messaging node:
 * N support conversations, each with a member socket and a staff socket, every socket posting a message
 * every {@code intervalMs}. Reports the p50/p95/p99/max of the ack latency (send to {@code ack}) and of
 * the fan-out latency (send to the {@code message} frame on either socket), then dumps the
 * {@code messaging.*}, R2DBC pool, Kafka offset and heap lines of {@code /actuator/prometheus}.
 *
 * <p>Support conversations are used because they need no announce lookup (no announce-management). The
 * rows stay in the read model; remove them with
 * {@code delete from announce_conversation where subject like 'load %'} (and the participant and message
 * rows keyed by those ids) when they are in the way.
 *
 * <p>Run from the module, with the node already up:
 * <pre>
 * mvn -pl messaging -Dmaven.test.skip=true test-compile exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=org.linlinjava.announce.messaging.load.ChatLoadDriver \
 *     -Dload.base=http://localhost:8086 -Dload.conversations=20 -Dload.seconds=60 -Dload.intervalMs=3000
 * </pre>
 * {@code -Dload.prefill=200} first posts 200 messages into every conversation over REST, so each post of the run
 * works on a long history (the state cache experiment of section 6, step 2).
 */
public final class ChatLoadDriver {

    static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };
    static final ParameterizedTypeReference<Map<String, Object>> BODY = new ParameterizedTypeReference<>() {
    };
    static final ObjectMapper JSON = new ObjectMapper();

    final String base;
    final int conversations;
    final Duration duration;
    final long intervalMs;
    final int prefill;
    final WebClient http;
    final List<Long> ackNanos = new ArrayList<>();
    final List<Long> fanOutNanos = new ArrayList<>();
    final AtomicLong sent = new AtomicLong();
    final AtomicLong errors = new AtomicLong();
    final List<Socket> sockets = new ArrayList<>();
    volatile boolean closing;

    ChatLoadDriver(String base, int conversations, Duration duration, long intervalMs, int prefill) {
        this.base = base;
        this.conversations = conversations;
        this.duration = duration;
        this.intervalMs = intervalMs;
        this.prefill = prefill;
        this.http = WebClient.builder().baseUrl(base).build();
    }

    public static void main(String[] args) throws Exception {
        ChatLoadDriver driver = new ChatLoadDriver(
                System.getProperty("load.base", "http://localhost:8086"),
                Integer.getInteger("load.conversations", 20),
                Duration.ofSeconds(Integer.getInteger("load.seconds", 60)),
                Long.getLong("load.intervalMs", 3000L),
                Integer.getInteger("load.prefill", 0));
        driver.run();
        System.exit(0);
    }

    void run() throws Exception {
        System.out.printf("base=%s conversations=%d duration=%s interval=%dms prefill=%d%n", base, conversations, duration, intervalMs, prefill);
        List<String> ids = new ArrayList<>();
        List<Integer> members = new ArrayList<>();
        for (int i = 0; i < conversations; i++) {
            int member = 100_000 + i;
            int staff = 9_000 + (i % 5);
            Map<String, Object> opened = http.post().uri("/api/conversations").header("X-User-Id", String.valueOf(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("type", "SUPPORT", "subject", "load " + i + " " + UUID.randomUUID().toString().substring(0, 8)))
                    .retrieve().bodyToMono(BODY).block(Duration.ofSeconds(30));
            String id = String.valueOf(opened.get("id"));
            http.post().uri("/api/admin/conversations/" + id + "/join").header("X-Staff-Id", String.valueOf(staff))
                    .retrieve().toBodilessEntity().block(Duration.ofSeconds(30));
            awaitOpen(id, member);
            ids.add(id);
            members.add(member);
        }
        prefill(ids, members);
        for (int i = 0; i < conversations; i++) {
            String id = ids.get(i);
            int member = members.get(i);
            int staff = 9_000 + (i % 5);
            Map<String, Long> pending = new ConcurrentHashMap<>();
            sockets.add(new Socket(id, "X-User-Id", member, pending).connect());
            sockets.add(new Socket(id, "X-Staff-Id", staff, pending).connect());
        }
        System.out.printf("%d conversations opened and joined, %d sockets connected%n", ids.size(), sockets.size());
        Thread.sleep(2_000);

        long end = System.nanoTime() + duration.toNanos();
        List<Thread> senders = new ArrayList<>();
        for (Socket socket : sockets) {
            Thread t = new Thread(() -> {
                try {
                    Thread.sleep(ThreadLocalRandom.current().nextLong(intervalMs));
                    while (System.nanoTime() < end) {
                        socket.post();
                        Thread.sleep(intervalMs);
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
            t.setDaemon(true);
            t.start();
            senders.add(t);
        }
        for (Thread t : senders) {
            t.join();
        }
        Thread.sleep(5_000);
        closing = true;
        sockets.forEach(Socket::close);

        System.out.println();
        System.out.printf("sent=%d acks=%d fan-out samples=%d (expected %d) errors=%d%n", sent.get(), ackNanos.size(), fanOutNanos.size(),
                sent.get() * 2, errors.get());
        report("ack latency    ", ackNanos);
        report("fan-out latency", fanOutNanos);
        System.out.println();
        System.out.println("--- /actuator/prometheus (messaging, pool, kafka offset, heap)");
        String scrape = http.get().uri("/actuator/prometheus").retrieve().bodyToMono(String.class).block(Duration.ofSeconds(30));
        scrape.lines().filter(l -> !l.startsWith("#"))
                .filter(l -> l.startsWith("messaging_") || l.startsWith("r2dbc_pool") || l.contains("binder_kafka_offset")
                        || l.startsWith("jvm_memory_used_bytes{area=\"heap\"") || l.startsWith("jvm_threads_live"))
                .filter(l -> !l.contains("_bucket{"))
                .forEach(System.out::println);
    }

    /**
     * Makes every conversation {@code prefill} messages long before the sockets start, so the command path replays
     * (or, with the state cache, skips) a realistic history on each post. Conversations are filled in parallel,
     * each one sequentially so the posts of one conversation never race each other.
     */
    void prefill(List<String> ids, List<Integer> members) {
        if (prefill <= 0) {
            return;
        }
        long started = System.nanoTime();
        Flux.range(0, ids.size())
                .flatMap(i -> Flux.range(1, prefill).concatMap(n -> http.post().uri("/api/conversations/" + ids.get(i) + "/messages")
                        .header("X-User-Id", String.valueOf(members.get(i))).contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("clientMsgId", UUID.randomUUID().toString(), "content", "prefill " + n))
                        .retrieve().toBodilessEntity()), ids.size())
                .then().block(Duration.ofMinutes(30));
        System.out.printf("prefilled %d messages into each of %d conversations in %d s%n", prefill, ids.size(),
                TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started));
    }

    /**
     * Waits until the read model shows the conversation OPEN (the staff join has been projected). The socket
     * handshake no longer needs this: since 2026-10-01 it looks a conversation up again when the read model does
     * not have it yet ({@code messaging.attach.*}). The wait stays so the runs remain comparable with the baseline.
     */
    void awaitOpen(String id, int member) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            Map<String, Object> view = http.get().uri("/api/conversations/" + id).header("X-User-Id", String.valueOf(member))
                    .retrieve().bodyToMono(BODY).onErrorResume(e -> Mono.empty()).block(Duration.ofSeconds(30));
            if (view != null && "OPEN".equals(view.get("status"))) {
                return;
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("conversation " + id + " never became OPEN in the read model");
    }

    static void report(String label, List<Long> samples) {
        List<Long> sorted;
        synchronized (samples) {
            sorted = new ArrayList<>(samples);
        }
        if (sorted.isEmpty()) {
            System.out.println(label + ": no samples");
            return;
        }
        sorted.sort(null);
        System.out.printf("%s: n=%d p50=%dms p95=%dms p99=%dms max=%dms%n", label, sorted.size(),
                ms(percentile(sorted, 0.50)), ms(percentile(sorted, 0.95)), ms(percentile(sorted, 0.99)), ms(sorted.get(sorted.size() - 1)));
    }

    static long percentile(List<Long> sorted, double p) {
        int index = (int) Math.ceil(p * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    static long ms(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(nanos);
    }

    /** One WebSocket client of one conversation; {@code pending} (send time by client message id) is shared with its peer. */
    final class Socket {
        final String conversationId;
        final String header;
        final int principal;
        final Map<String, Long> pending;
        final Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();
        final CountDownLatch open = new CountDownLatch(1);
        Disposable connection;

        Socket(String conversationId, String header, int principal, Map<String, Long> pending) {
            this.conversationId = conversationId;
            this.header = header;
            this.principal = principal;
            this.pending = pending;
        }

        Socket connect() throws InterruptedException {
            HttpHeaders headers = new HttpHeaders();
            headers.add(header, String.valueOf(principal));
            String ws = base.replaceFirst("^http", "ws") + "/ws/conversations/" + conversationId;
            connection = new ReactorNettyWebSocketClient().execute(URI.create(ws), headers, session -> {
                open.countDown();
                session.closeStatus().filter(status -> !closing && status.getCode() != 1000 && status.getCode() != 1005)
                        .subscribe(status -> {
                            errors.incrementAndGet();
                            System.err.println("socket " + conversationId + "/" + principal + " closed: " + status);
                        });
                Mono<Void> send = session.send(outbound.asFlux().map(session::textMessage));
                Mono<Void> receive = session.receive().map(WebSocketMessage::getPayloadAsText).doOnNext(this::received).then();
                return Mono.when(send, receive);
            }).subscribe(v -> {
            }, e -> {
                errors.incrementAndGet();
                System.err.println("socket " + conversationId + "/" + principal + " failed: " + e);
            });
            if (!open.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("socket did not open: " + ws);
            }
            return this;
        }

        void post() {
            String clientMsgId = UUID.randomUUID().toString();
            pending.put(clientMsgId, System.nanoTime());
            try {
                outbound.tryEmitNext(JSON.writeValueAsString(Map.of("type", "post", "clientMsgId", clientMsgId,
                        "content", "load " + principal + " " + sent.incrementAndGet())));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        void received(String text) {
            Map<String, Object> frame;
            try {
                frame = JSON.readValue(text, MAP);
            } catch (Exception e) {
                errors.incrementAndGet();
                return;
            }
            String type = String.valueOf(frame.get("type"));
            Object clientMsgId = frame.get("clientMsgId");
            Long sentAt = clientMsgId == null ? null : pending.get(String.valueOf(clientMsgId));
            if (sentAt == null) {
                if ("error".equals(type)) {
                    errors.incrementAndGet();
                    System.err.println("error frame: " + text);
                }
                return;
            }
            long elapsed = System.nanoTime() - sentAt;
            if ("ack".equals(type)) {
                synchronized (ackNanos) {
                    ackNanos.add(elapsed);
                }
            } else if ("message".equals(type)) {
                synchronized (fanOutNanos) {
                    fanOutNanos.add(elapsed);
                }
            } else if ("error".equals(type)) {
                errors.incrementAndGet();
                System.err.println("error frame: " + text);
            }
        }

        void close() {
            outbound.tryEmitComplete();
            if (connection != null) {
                connection.dispose();
            }
        }
    }
}
