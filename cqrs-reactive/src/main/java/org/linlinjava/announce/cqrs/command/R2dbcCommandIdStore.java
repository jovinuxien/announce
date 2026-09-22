package org.linlinjava.announce.cqrs.command;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Command log in the service's MySQL database (table {@code cqrs_command_log} by default), so a retried
 * command is recognised across restarts and instances. The primary key on {@code command_id} makes the
 * claim race-free: the second concurrent claim fails its insert and reads the first one's row.
 */
public class R2dbcCommandIdStore implements CommandIdStore {

    private final DatabaseClient client;
    private final String table;

    public R2dbcCommandIdStore(DatabaseClient client, String table) {
        this.client = Objects.requireNonNull(client, "client");
        this.table = Objects.requireNonNull(table, "table");
    }

    /** MySQL DDL of the command log table. */
    public static String ddl(String table) {
        return "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "command_id VARCHAR(128) NOT NULL, "
                + "subject VARCHAR(512) NOT NULL, "
                + "completed TINYINT(1) NOT NULL DEFAULT 0, "
                + "result_json MEDIUMTEXT NULL, "
                + "created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), "
                + "completed_at TIMESTAMP(3) NULL, "
                + "PRIMARY KEY (command_id))";
    }

    public Mono<Void> createTableIfMissing() {
        return client.sql(ddl(table)).then();
    }

    @Override
    public Mono<Claim> claim(String commandId, String subject) {
        return client.sql("INSERT INTO " + table + " (command_id, subject) VALUES (:id, :subject)")
                .bind("id", commandId)
                .bind("subject", subject)
                .fetch().rowsUpdated()
                .map(rows -> Claim.fresh())
                .onErrorResume(DataIntegrityViolationException.class, duplicate -> existing(commandId));
    }

    private Mono<Claim> existing(String commandId) {
        return client.sql("SELECT completed, result_json FROM " + table + " WHERE command_id = :id")
                .bind("id", commandId)
                .map((row, meta) -> {
                    Number completed = row.get("completed", Number.class);
                    String result = row.get("result_json", String.class);
                    return completed != null && completed.intValue() != 0 ? Claim.completed(result) : Claim.inFlight();
                })
                .one()
                // the row vanished between the failed insert and the select (released by a failing first attempt):
                // treat it as in flight; the client retries once more
                .defaultIfEmpty(Claim.inFlight());
    }

    @Override
    public Mono<Void> complete(String commandId, String resultJson) {
        DatabaseClient.GenericExecuteSpec spec = client.sql("UPDATE " + table
                        + " SET completed = 1, result_json = :result, completed_at = CURRENT_TIMESTAMP(3) WHERE command_id = :id")
                .bind("id", commandId);
        spec = resultJson == null ? spec.bindNull("result", String.class) : spec.bind("result", resultJson);
        return spec.fetch().rowsUpdated().then();
    }

    @Override
    public Mono<Void> release(String commandId) {
        return client.sql("DELETE FROM " + table + " WHERE command_id = :id")
                .bind("id", commandId)
                .fetch().rowsUpdated().then();
    }
}
