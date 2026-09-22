package org.linlinjava.announce.cqrs.readside;

import com.opencqrs.framework.eventhandler.progress.Progress;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Stores the checkpoint of each event handling group and partition in the service's MySQL database
 * (table {@code cqrs_readside_progress} by default), next to the read model it belongs to.
 *
 * <p>OpenCQRS calls {@link #proceed} once per event on the processor thread: the handler runs inside the
 * supplier and the returned progress is written afterwards. Read-model write and checkpoint are two
 * statements, so a crash in between replays the last event once more (at-least-once); projections must
 * therefore be idempotent.
 */
public class R2dbcProgressTracker implements ResettableProgressTracker {

    private final DatabaseClient client;
    private final String table;
    private final Duration timeout;

    public R2dbcProgressTracker(DatabaseClient client, String table) {
        this(client, table, Duration.ofSeconds(30));
    }

    public R2dbcProgressTracker(DatabaseClient client, String table, Duration timeout) {
        this.client = Objects.requireNonNull(client, "client");
        this.table = Objects.requireNonNull(table, "table");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
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

    @Override
    public Progress current(String group, long partition) {
        return currentAsync(group, partition).block(timeout);
    }

    public Mono<Progress> currentAsync(String group, long partition) {
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
            store(group, partition, success.id()).block(timeout);
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

    @Override
    public Mono<Void> reset(String group) {
        return client.sql("DELETE FROM " + table + " WHERE group_id = :groupId")
                .bind("groupId", group)
                .fetch().rowsUpdated().then();
    }
}
