package org.linlinjava.announce.messaging.load;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The read load of tuning step 7 ({@code doc/messaging-architecture.md} section 6) against a running messaging
 * node: one member who takes part in N support conversations and one staff member who joined them all, then
 * {@code threads} clients fetching, in turn, the member's inbox, the staff member's inbox and the staff queue
 * ({@code status=OPEN}) with pages of 20 and 50 for {@code seconds}. Reports the request latency p50/p95/p99/max
 * and the rate per query and page size, then the {@code messaging.query.*} and R2DBC pool lines of
 * {@code /actuator/prometheus}.
 *
 * <p>The conversations are support conversations (no announce lookup). Their subject starts with {@code load },
 * so the clean-up of the other drivers removes them: {@code delete from announce_conversation where subject like
 * 'load %'} (and the participant rows keyed by those ids).
 *
 * <p>Run from the module, with the node already up:
 * <pre>
 * mvn -pl messaging -DskipTests test-compile exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=org.linlinjava.announce.messaging.load.InboxLoadDriver \
 *     -Dinbox.base=http://localhost:8086 -Dinbox.conversations=50 -Dinbox.seconds=60 -Dinbox.threads=4
 * </pre>
 */
public final class InboxLoadDriver {

    static final ParameterizedTypeReference<Map<String, Object>> BODY = new ParameterizedTypeReference<>() {
    };
    static final int MEMBER = 200_000;
    static final int STAFF = 9_950;
    static final int[] PAGE_SIZES = {20, 50};

    final String base;
    final int conversations;
    final Duration duration;
    final int threads;
    final WebClient http;
    final Map<String, List<Long>> samples = new LinkedHashMap<>();
    final AtomicLong requests = new AtomicLong();
    final AtomicLong errors = new AtomicLong();

    InboxLoadDriver(String base, int conversations, Duration duration, int threads) {
        this.base = base;
        this.conversations = conversations;
        this.duration = duration;
        this.threads = threads;
        this.http = WebClient.builder().baseUrl(base).build();
        for (String query : new String[]{"member inbox", "staff inbox", "staff queue"}) {
            for (int size : PAGE_SIZES) {
                samples.put(label(query, size), new ArrayList<>());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        InboxLoadDriver driver = new InboxLoadDriver(
                System.getProperty("inbox.base", "http://localhost:8086"),
                Integer.getInteger("inbox.conversations", 50),
                Duration.ofSeconds(Integer.getInteger("inbox.seconds", 60)),
                Integer.getInteger("inbox.threads", 4));
        driver.run();
        System.exit(0);
    }

    static String label(String query, int size) {
        return query + " size=" + size;
    }

    void run() throws Exception {
        System.out.printf("base=%s conversations=%d duration=%s threads=%d%n", base, conversations, duration, threads);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < conversations; i++) {
            Map<String, Object> opened = http.post().uri("/api/conversations").header("X-User-Id", String.valueOf(MEMBER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("type", "SUPPORT", "subject", "load inbox " + i + " " + UUID.randomUUID().toString().substring(0, 8)))
                    .retrieve().bodyToMono(BODY).block(Duration.ofSeconds(30));
            String id = String.valueOf(opened.get("id"));
            http.post().uri("/api/admin/conversations/" + id + "/join").header("X-Staff-Id", String.valueOf(STAFF))
                    .retrieve().toBodilessEntity().block(Duration.ofSeconds(30));
            ids.add(id);
        }
        awaitProjected(ids.get(ids.size() - 1));
        System.out.printf("%d conversations opened and joined by staff %d; member %d%n", ids.size(), STAFF, MEMBER);
        warmUp();

        long end = System.nanoTime() + duration.toNanos();
        List<Thread> clients = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int offset = t;
            Thread thread = new Thread(() -> {
                int turn = offset;
                while (System.nanoTime() < end) {
                    int size = PAGE_SIZES[turn % PAGE_SIZES.length];
                    switch ((turn / PAGE_SIZES.length) % 3) {
                        case 0 -> fetch("member inbox", size, "/api/conversations", "X-User-Id", MEMBER);
                        case 1 -> fetch("staff inbox", size, "/api/conversations", "X-Staff-Id", STAFF);
                        default -> fetch("staff queue", size, "/api/admin/conversations?status=OPEN", "X-Staff-Id", STAFF);
                    }
                    turn++;
                }
            });
            thread.setDaemon(true);
            thread.start();
            clients.add(thread);
        }
        for (Thread thread : clients) {
            thread.join();
        }
        System.out.printf("requests=%d errors=%d over %s = %.1f requests/s%n", requests.get(), errors.get(), duration,
                requests.get() / (double) duration.toSeconds());
        samples.forEach(ChatLoadDriver::report);
        System.out.println("--- /actuator/prometheus (messaging.query, pool)");
        String scrape = http.get().uri("/actuator/prometheus").retrieve().bodyToMono(String.class).block(Duration.ofSeconds(30));
        if (scrape != null) {
            scrape.lines().filter(line -> !line.startsWith("#"))
                    .filter(line -> line.startsWith("messaging_query") || line.startsWith("r2dbc_pool"))
                    .forEach(System.out::println);
        }
    }

    /** Each thread fetches every query once before the clock starts, so connection set-up is not in the samples. */
    void warmUp() {
        for (int size : PAGE_SIZES) {
            fetch("member inbox", size, "/api/conversations", "X-User-Id", MEMBER);
            fetch("staff inbox", size, "/api/conversations", "X-Staff-Id", STAFF);
            fetch("staff queue", size, "/api/admin/conversations?status=OPEN", "X-Staff-Id", STAFF);
        }
        samples.values().forEach(List::clear);
    }

    void fetch(String query, int size, String path, String header, int principal) {
        String uri = path + (path.contains("?") ? "&" : "?") + "page=1&size=" + size;
        long start = System.nanoTime();
        try {
            Map<String, Object> page = http.get().uri(uri).header(header, String.valueOf(principal))
                    .retrieve().bodyToMono(BODY).block(Duration.ofSeconds(30));
            long elapsed = System.nanoTime() - start;
            int items = page == null ? -1 : ((List<?>) page.get("items")).size();
            if (items != Math.min(size, conversations)) {
                errors.incrementAndGet();
                System.err.printf("%s size=%d answered %d items%n", query, size, items);
            }
            List<Long> list = samples.get(label(query, size));
            synchronized (list) {
                list.add(elapsed);
            }
        } catch (RuntimeException e) {
            errors.incrementAndGet();
            if (errors.get() <= 3) {
                System.err.printf("%s size=%d failed: %s%n", query, size, e);
            }
        } finally {
            requests.incrementAndGet();
        }
    }

    /** Waits until the read model shows the last conversation OPEN (the staff join has been projected). */
    void awaitProjected(String id) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline) {
            Map<String, Object> view = http.get().uri("/api/conversations/" + id).header("X-User-Id", String.valueOf(MEMBER))
                    .retrieve().bodyToMono(BODY).onErrorResume(e -> Mono.empty()).block(Duration.ofSeconds(30));
            if (view != null && "OPEN".equals(view.get("status"))) {
                return;
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("conversation " + id + " never became OPEN in the read model");
    }
}
