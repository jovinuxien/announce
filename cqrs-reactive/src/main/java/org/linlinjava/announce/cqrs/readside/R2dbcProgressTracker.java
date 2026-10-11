package org.linlinjava.announce.cqrs.readside;

import com.opencqrs.framework.eventhandler.progress.Progress;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stores the checkpoint of each event handling group and partition in the service's MySQL database
 * (table {@code cqrs_readside_progress} by default), next to the read model it belongs to.
 *
 * <p>OpenCQRS calls {@link #proceed} once per event on the processor thread: the handler runs inside the
 * supplier and the returned progress is recorded afterwards. Read-model write and checkpoint are separate, so
 * a crash in between replays the last event once more (at-least-once); projections must therefore be
 * idempotent.
 *
 * <p>Coalesced checkpoints (since 2026-10-11, tuning step 10 of the messaging service): with a flush interval,
 * {@link #proceed} only records the latest event id of the partition in memory and a flusher thread writes the
 * pending ids at most once per interval, so the processor thread does not pay a MySQL round trip for every
 * event of the stream (OpenCQRS proceeds on every observed event, the ones of other partitions included, and
 * that round trip was most of a processor's time under load). After a crash the processors replay at most one
 * interval of events, which is the same at-least-once contract. {@link #current} and {@link #checkpoints}
 * answer with the pending id when there is one, so readers see the latest position. An interval of zero keeps
 * the write per event.
 *
 * <p>Partitions: OpenCQRS keeps one checkpoint per partition and a partition without one starts at the first
 * event of the log. Raising {@code life-cycle.partitions} of a group therefore needs the new partitions seeded
 * from the existing progress, which {@link #seedPartitions} does from the group's lowest checkpoint: never
 * ahead of any partition (no event is skipped), at worst a short replay when the group was not caught up.
 */
public class R2dbcProgressTracker implements ResettableProgressTracker, AutoCloseable {

    private static final Logger log = Logger.getLogger(R2dbcProgressTracker.class.getName());

    private record Key(String group, long partition) {
    }

    private final DatabaseClient client;
    private final String table;
    private final Duration timeout;
    private final Duration flushInterval;
    private final ConcurrentHashMap<Key, String> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService flusher;

    public R2dbcProgressTracker(DatabaseClient client, String table) {
        this(client, table, Duration.ofSeconds(30));
    }

    /** A tracker that writes every checkpoint at once (flush interval zero). */
    public R2dbcProgressTracker(DatabaseClient client, String table, Duration timeout) {
        this(client, table, timeout, Duration.ZERO);
    }

    /**
     * @param flushInterval how often pending checkpoints are written; zero writes each one on the processor
     *                      thread as it is reported
     */
    public R2dbcProgressTracker(DatabaseClient client, String table, Duration timeout, Duration flushInterval) {
        this.client = Objects.requireNonNull(client, "client");
        this.table = Objects.requireNonNull(table, "table");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.flushInterval = Objects.requireNonNull(flushInterval, "flushInterval");
        if (flushInterval.isZero() || flushInterval.isNegative()) {
            this.flusher = null;
        } else {
            this.flusher = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "cqrs-progress-flush");
                thread.setDaemon(true);
                return thread;
            });
            this.flusher.scheduleWithFixedDelay(this::flushQuietly, flushInterval.toMillis(), flushInterval.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /** MySQL DDL of the progress table. */
    public static String ddl(String table) {
        return "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "group_id VARCHAR(190) NOT NULL, "
                + "partition_no BIGINT NOT NULL, "
                + "event_id VARCHAR(64) NOT NULL, "
                + "updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), "
                + "PRIMARY KEY (group_id, partition_no))";
    }

    public Mono<Void> createTableIfMissing() {
        return client.sql(ddl(table)).then();
    }

    /** The flush interval, zero when every checkpoint is written at once. */
    public Duration flushInterval() {
        return flushInterval;
    }

    @Override
    public Progress current(String group, long partition) {
        return currentAsync(group, partition).block(timeout);
    }

    public Mono<Progress> currentAsync(String group, long partition) {
        String inFlight = pending.get(new Key(group, partition));
        if (inFlight != null) {
            return Mono.just(new Progress.Success(inFlight));
        }
        return client.sql("SELECT event_id FROM " + table + " WHERE group_id = :groupId AND partition_no = :partition")
                .bind("groupId", group)
                .bind("partition", partition)
                .map((row, meta) -> (Progress) new Progress.Success(row.get("event_id", String.class)))
                .one()
                .defaultIfEmpty(new Progress.None());
    }

    @Override
    public void proceed(String group, long partition, Supplier<Progress> execution) {
        Progress progress = execution.get();
        if (progress instanceof Progress.Success success) {
            if (flusher == null) {
                store(group, partition, success.id()).block(timeout);
            } else {
                pending.put(new Key(group, partition), success.id());
            }
        }
    }

    public Mono<Void> store(String group, long partition, String eventId) {
        return client.sql("INSERT INTO " + table + " (group_id, partition_no, event_id) VALUES (:groupId, :partition, :eventId)"
                        + " ON DUPLICATE KEY UPDATE event_id = :eventId, updated_at = CURRENT_TIMESTAMP(3)")
                .bind("groupId", group)
                .bind("partition", partition)
                .bind("eventId", eventId)
                .fetch().rowsUpdated().then();
    }

    /**
     * Writes every pending checkpoint, one statement each; an id that advanced while it was being written stays
     * pending for the next flush. Blocks the calling thread; the flusher thread calls it on its schedule.
     */
    public void flush() {
        if (pending.isEmpty()) {
            return;
        }
        Map<Key, String> snapshot = new LinkedHashMap<>(pending);
        Flux.fromIterable(snapshot.entrySet())
                .concatMap(entry -> store(entry.getKey().group(), entry.getKey().partition(), entry.getValue())
                        .doOnSuccess(v -> pending.remove(entry.getKey(), entry.getValue())))
                .then()
                .block(timeout);
    }

    private void flushQuietly() {
        try {
            flush();
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Checkpoint flush failed, the pending checkpoints stay for the next one", e);
        }
    }

    /** Pending checkpoints not yet written, for tests and gauges. */
    public int pendingCount() {
        return pending.size();
    }

    /** Checkpoints of a group by partition: the stored ones, overlaid with the pending ones. */
    public Mono<Map<Long, String>> checkpoints(String group) {
        return client.sql("SELECT partition_no, event_id FROM " + table + " WHERE group_id = :groupId ORDER BY partition_no")
                .bind("groupId", group)
                .map((row, meta) -> Map.entry(row.get("partition_no", Long.class), row.get("event_id", String.class)))
                .all()
                .collect(LinkedHashMap<Long, String>::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()))
                .map(stored -> {
                    pending.forEach((key, id) -> {
                        if (key.group().equals(group)) {
                            stored.put(key.partition(), id);
                        }
                    });
                    return stored;
                });
    }

    /**
     * Inserts a checkpoint for every partition in {@code [0, partitions)} that has none, copying the lowest
     * checkpoint the group already has. A group without any checkpoint is left alone (it starts from the
     * beginning, as a new projection should). Returns the number of partitions seeded. Run it while the group's
     * processors are stopped on every node, as OpenCQRS requires for a change of partitioning.
     */
    public Mono<Integer> seedPartitions(String group, long partitions) {
        if (partitions <= 1) {
            return Mono.just(0);
        }
        return checkpoints(group).flatMap(existing -> {
            if (existing.isEmpty()) {
                return Mono.just(0);
            }
            String lowest = existing.values().stream().min(EVENT_ID_ORDER).orElseThrow();
            return Flux.range(0, (int) partitions)
                    .map(Integer::longValue)
                    .filter(partition -> !existing.containsKey(partition))
                    .concatMap(partition -> store(group, partition, lowest).thenReturn(1))
                    .reduce(0, Integer::sum);
        });
    }

    /** EventSourcingDB ids are decimal strings; compare them as numbers when both parse, as text otherwise. */
    static final Comparator<String> EVENT_ID_ORDER = (a, b) -> {
        try {
            return Long.compare(Long.parseLong(a), Long.parseLong(b));
        } catch (NumberFormatException notNumeric) {
            return a.compareTo(b);
        }
    };

    @Override
    public Mono<Void> reset(String group) {
        return Mono.fromRunnable(() -> pending.keySet().removeIf(key -> key.group().equals(group)))
                .then(client.sql("DELETE FROM " + table + " WHERE group_id = :groupId")
                        .bind("groupId", group)
                        .fetch().rowsUpdated().then());
    }

    /** Writes what is pending and stops the flusher; Spring calls it when the context closes. */
    @Override
    public void close() {
        if (flusher == null) {
            return;
        }
        flusher.shutdown();
        try {
            flusher.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        flushQuietly();
    }
}
