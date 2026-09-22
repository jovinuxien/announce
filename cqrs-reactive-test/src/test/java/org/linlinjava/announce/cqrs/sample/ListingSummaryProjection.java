package org.linlinjava.announce.cqrs.sample;

import io.r2dbc.spi.ConnectionFactory;
import org.linlinjava.announce.cqrs.command.Subjects;
import org.linlinjava.announce.cqrs.readside.ProjectionHandlers;
import org.linlinjava.announce.cqrs.readside.ReactiveProjection;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

/**
 * Read model {@code listing_summary} in the service's MySQL database; the checkpoint of group
 * {@code listing-summary} lives in {@code cqrs_readside_progress} in the same database.
 */
@Component
public class ListingSummaryProjection implements ReactiveProjection {

    public static final String GROUP = "listing-summary";

    public record Summary(String id, String title, long priceCents, String status, String lastEventId) {
    }

    private final DatabaseClient client;

    public ListingSummaryProjection(ConnectionFactory connectionFactory) {
        this.client = DatabaseClient.create(connectionFactory);
    }

    @Override
    public String group() {
        return GROUP;
    }

    @Override
    public void configure(ProjectionHandlers handlers) {
        handlers.on(ListingCreated.class, (event, envelope) -> client.sql(
                        "INSERT INTO listing_summary (id, title, price_cents, status, last_event_id) VALUES (:id, :title, :price, 'OPEN', :eventId)"
                                + " ON DUPLICATE KEY UPDATE title = :title, price_cents = :price, status = 'OPEN', last_event_id = :eventId")
                .bind("id", Subjects.idOf(envelope.subject())).bind("title", event.title()).bind("price", event.priceCents())
                .bind("eventId", envelope.eventId()).fetch().rowsUpdated().then());
        handlers.on(ListingPriceChanged.class, (event, envelope) -> client.sql(
                        "UPDATE listing_summary SET price_cents = :price, last_event_id = :eventId WHERE id = :id")
                .bind("id", Subjects.idOf(envelope.subject())).bind("price", event.priceCents())
                .bind("eventId", envelope.eventId()).fetch().rowsUpdated().then());
        handlers.on(ListingClosed.class, (event, envelope) -> client.sql(
                        "UPDATE listing_summary SET status = 'CLOSED', last_event_id = :eventId WHERE id = :id")
                .bind("id", Subjects.idOf(envelope.subject())).bind("eventId", envelope.eventId()).fetch().rowsUpdated().then());
    }

    @Override
    public Mono<Void> prepare() {
        return client.sql("CREATE TABLE IF NOT EXISTS listing_summary ("
                + "id VARCHAR(64) NOT NULL, title VARCHAR(255) NOT NULL, price_cents BIGINT NOT NULL, "
                + "status VARCHAR(16) NOT NULL, last_event_id VARCHAR(64) NOT NULL, PRIMARY KEY (id))").then();
    }

    @Override
    public Mono<Void> reset() {
        return client.sql("TRUNCATE TABLE listing_summary").then();
    }

    public Mono<Optional<Summary>> find(String id) {
        return client.sql("SELECT id, title, price_cents, status, last_event_id FROM listing_summary WHERE id = :id")
                .bind("id", id)
                .map((row, meta) -> new Summary(row.get("id", String.class), row.get("title", String.class),
                        row.get("price_cents", Long.class), row.get("status", String.class), row.get("last_event_id", String.class)))
                .one().map(Optional::of).defaultIfEmpty(Optional.empty());
    }

    public Mono<Map<String, Object>> progress() {
        return client.sql("SELECT group_id, partition_no, event_id FROM cqrs_readside_progress WHERE group_id = :group")
                .bind("group", GROUP)
                .map((row, meta) -> Map.<String, Object>of("group", row.get("group_id", String.class),
                        "partition", row.get("partition_no", Long.class), "eventId", row.get("event_id", String.class)))
                .one();
    }
}
