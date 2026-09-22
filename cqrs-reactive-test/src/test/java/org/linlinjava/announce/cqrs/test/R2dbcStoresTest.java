package org.linlinjava.announce.cqrs.test;

import com.opencqrs.framework.eventhandler.progress.Progress;
import io.asyncer.r2dbc.mysql.MySqlConnectionConfiguration;
import io.asyncer.r2dbc.mysql.MySqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.cqrs.command.CommandIdStore;
import org.linlinjava.announce.cqrs.command.R2dbcCommandIdStore;
import org.linlinjava.announce.cqrs.readside.R2dbcProgressTracker;
import org.linlinjava.announce.cqrs.test.containers.CqrsContainers;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class R2dbcStoresTest {

    @Container
    static final MySQLContainer<?> mysql = CqrsContainers.mysql();

    static DatabaseClient client;

    @BeforeAll
    static void connect() {
        ConnectionFactory connectionFactory = MySqlConnectionFactory.from(MySqlConnectionConfiguration.builder()
                .host(mysql.getHost()).port(mysql.getMappedPort(MySQLContainer.MYSQL_PORT)).database(mysql.getDatabaseName())
                .username(mysql.getUsername()).password(mysql.getPassword()).build());
        client = DatabaseClient.create(connectionFactory);
    }

    @Test
    void progressTrackerStoresAndResetsCheckpoints() {
        R2dbcProgressTracker tracker = new R2dbcProgressTracker(client, "cqrs_readside_progress", Duration.ofSeconds(10));
        tracker.createTableIfMissing().block();

        assertThat(tracker.current("g1", 0)).isInstanceOf(Progress.None.class);
        tracker.proceed("g1", 0, () -> new Progress.Success("ev-1"));
        tracker.proceed("g1", 0, () -> new Progress.Success("ev-2"));
        tracker.proceed("g1", 1, () -> new Progress.Success("ev-3"));
        tracker.proceed("g1", 1, Progress.None::new);

        assertThat(tracker.current("g1", 0)).isEqualTo(new Progress.Success("ev-2"));
        assertThat(tracker.current("g1", 1)).isEqualTo(new Progress.Success("ev-3"));
        assertThat(tracker.current("g2", 0)).isInstanceOf(Progress.None.class);

        StepVerifier.create(tracker.reset("g1")).verifyComplete();
        assertThat(tracker.current("g1", 0)).isInstanceOf(Progress.None.class);
    }

    @Test
    void commandLogClaimsOnceAndReplaysCompletedResults() {
        R2dbcCommandIdStore store = new R2dbcCommandIdStore(client, "cqrs_command_log");
        store.createTableIfMissing().block();

        StepVerifier.create(store.claim("c1", "/listing/1")).expectNext(CommandIdStore.Claim.fresh()).verifyComplete();
        StepVerifier.create(store.claim("c1", "/listing/1")).expectNext(CommandIdStore.Claim.inFlight()).verifyComplete();
        StepVerifier.create(store.complete("c1", "\"1\"")).verifyComplete();
        StepVerifier.create(store.claim("c1", "/listing/1")).expectNext(CommandIdStore.Claim.completed("\"1\"")).verifyComplete();

        StepVerifier.create(store.claim("c2", "/listing/2")).expectNext(CommandIdStore.Claim.fresh()).verifyComplete();
        StepVerifier.create(store.release("c2")).verifyComplete();
        StepVerifier.create(store.claim("c2", "/listing/2")).expectNext(CommandIdStore.Claim.fresh()).verifyComplete();
        StepVerifier.create(store.complete("c2", null)).verifyComplete();
        StepVerifier.create(store.claim("c2", "/listing/2")).expectNext(CommandIdStore.Claim.completed(null)).verifyComplete();
    }

    @Test
    void concurrentClaimsOfTheSameIdYieldExactlyOneFreshClaim() {
        R2dbcCommandIdStore store = new R2dbcCommandIdStore(client, "cqrs_command_log");
        store.createTableIfMissing().block();

        List<CommandIdStore.Claim> claims = Flux.range(0, 8)
                .flatMap(i -> store.claim("race", "/listing/r"))
                .collectList().block(Duration.ofSeconds(10));

        assertThat(claims).hasSize(8);
        assertThat(claims.stream().filter(claim -> !claim.duplicate()).count()).isEqualTo(1);
    }
}
