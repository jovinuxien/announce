package org.linlinjava.announce.cqrs.test;

import com.opencqrs.framework.eventhandler.progress.Progress;
import io.asyncer.r2dbc.mysql.MySqlConnectionConfiguration;
import io.asyncer.r2dbc.mysql.MySqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.cqrs.command.CommandIdStore;
import org.linlinjava.announce.cqrs.command.R2dbcCommandIdStore;
import org.linlinjava.announce.cqrs.readside.R2dbcLockRegistry;
import org.linlinjava.announce.cqrs.readside.R2dbcProgressTracker;
import org.linlinjava.announce.cqrs.test.containers.CqrsContainers;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

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

    /**
     * With a flush interval the processor thread only records the checkpoint; a flusher writes it within the
     * interval, readers see the pending id at once, a reset forgets it, and close writes what is left.
     */
    @Test
    void progressTrackerCoalescesCheckpointsWhenGivenAFlushInterval() throws Exception {
        R2dbcProgressTracker direct = new R2dbcProgressTracker(client, "cqrs_readside_progress", Duration.ofSeconds(10));
        direct.createTableIfMissing().block();
        R2dbcProgressTracker tracker = new R2dbcProgressTracker(client, "cqrs_readside_progress", Duration.ofSeconds(10), Duration.ofMillis(200));
        try {
            for (int i = 1; i <= 50; i++) {
                tracker.proceed("g3", 0, () -> new Progress.Success("ev-" + 0));
                int n = i;
                tracker.proceed("g3", 0, () -> new Progress.Success("ev-" + n));
            }
            tracker.proceed("g3", 1, () -> new Progress.Success("ev-7"));

            assertThat(tracker.current("g3", 0)).as("the pending id is visible at once").isEqualTo(new Progress.Success("ev-50"));
            assertThat(tracker.checkpoints("g3").block()).containsEntry(0L, "ev-50").containsEntry(1L, "ev-7");
            assertThat(direct.current("g3", 0)).as("nothing written yet, or at most one flush").isIn(new Progress.None(), new Progress.Success("ev-50"));

            Thread.sleep(600);
            assertThat(direct.current("g3", 0)).as("written by the flusher within the interval").isEqualTo(new Progress.Success("ev-50"));
            assertThat(direct.current("g3", 1)).isEqualTo(new Progress.Success("ev-7"));
            assertThat(tracker.pendingCount()).isZero();

            tracker.proceed("g3", 0, () -> new Progress.Success("ev-51"));
            StepVerifier.create(tracker.reset("g3")).verifyComplete();
            assertThat(tracker.current("g3", 0)).as("a reset forgets the pending id too").isInstanceOf(Progress.None.class);

            tracker.proceed("g3", 2, () -> new Progress.Success("ev-9"));
        } finally {
            tracker.close();
        }
        assertThat(direct.current("g3", 2)).as("close writes what was pending").isEqualTo(new Progress.Success("ev-9"));
        StepVerifier.create(direct.reset("g3")).verifyComplete();
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

    @Test
    void partitionSeedingCopiesTheLowestCheckpointToNewPartitionsOnly() {
        R2dbcProgressTracker tracker = new R2dbcProgressTracker(client, "cqrs_readside_progress", Duration.ofSeconds(10));
        tracker.createTableIfMissing().block();
        tracker.reset("seed").block();
        tracker.reset("fresh").block();

        // a group that never ran keeps no checkpoint: its partitions all start from the first event
        assertThat(tracker.seedPartitions("fresh", 4).block()).isZero();
        assertThat(tracker.checkpoints("fresh").block()).isEmpty();

        // partitions 0 and 2 exist (ids compare as numbers: "900" is lower than "1200"); 1 and 3 get the lowest
        tracker.proceed("seed", 0, () -> new Progress.Success("1200"));
        tracker.proceed("seed", 2, () -> new Progress.Success("900"));
        assertThat(tracker.seedPartitions("seed", 4).block()).isEqualTo(2);
        assertThat(tracker.checkpoints("seed").block())
                .containsExactly(Map.entry(0L, "1200"), Map.entry(1L, "900"), Map.entry(2L, "900"), Map.entry(3L, "900"));

        // idempotent, and a single partition is never seeded
        assertThat(tracker.seedPartitions("seed", 4).block()).isZero();
        assertThat(tracker.seedPartitions("seed", 1).block()).isZero();
    }

    @Test
    void lockRegistryGivesOneLeaseToOneOwnerUntilItExpiresOrIsReleased() throws Exception {
        Duration ttl = Duration.ofMillis(600);
        Duration poll = Duration.ofMillis(50);
        R2dbcLockRegistry nodeA = new R2dbcLockRegistry(client, "cqrs_readside_lock", ttl, poll, Duration.ofSeconds(10), "node-a");
        R2dbcLockRegistry nodeB = new R2dbcLockRegistry(client, "cqrs_readside_lock", ttl, poll, Duration.ofSeconds(10), "node-b");
        nodeA.createTableIfMissing().block();

        Lock a = nodeA.obtain("group-0");
        Lock b = nodeB.obtain("group-0");

        // A takes the lease; B cannot, even when it waits a few polls
        assertThat(a.tryLock()).isTrue();
        assertThat(b.tryLock()).isFalse();
        assertThat(b.tryLock(200, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(nodeA.holders().block()).containsEntry("group-0", "node-a");

        // the holder renews by locking again (reentrant, as the leader initiator does every heartbeat) ...
        Thread.sleep(400);
        assertThat(a.tryLock()).isTrue();
        a.unlock();                                   // the initiator's unlock after a renewal: still held
        Thread.sleep(400);                            // past the first lease, inside the renewed one
        assertThat(b.tryLock()).isFalse();
        assertThat(nodeA.holders().block()).containsEntry("group-0", "node-a");

        // ... and an unrenewed lease is taken over once it has expired
        Thread.sleep(700);
        assertThat(b.tryLock()).isTrue();
        assertThat(nodeB.holders().block()).containsEntry("group-0", "node-b");
        assertThat(a.tryLock()).isFalse();            // A learns it lost the lease

        // a release frees the row at once; an unlock by a non-holder changes nothing
        a.unlock();
        assertThat(nodeB.holders().block()).containsEntry("group-0", "node-b");
        b.unlock();
        assertThat(nodeB.holders().block()).doesNotContainKey("group-0");
        assertThat(a.tryLock(1, TimeUnit.SECONDS)).isTrue();
        a.unlock();

        // keys are independent
        assertThat(nodeA.obtain("group-1").tryLock()).isTrue();
        assertThat(nodeB.obtain("group-2").tryLock()).isTrue();
        assertThat(nodeA.holders().block()).containsEntry("group-1", "node-a").containsEntry("group-2", "node-b");
        nodeA.obtain("group-1").unlock();
        nodeB.obtain("group-2").unlock();
    }

    @Test
    void lockRegistryContendersAgreeOnExactlyOneHolder() {
        List<R2dbcLockRegistry> nodes = java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> new R2dbcLockRegistry(client, "cqrs_readside_lock", Duration.ofSeconds(5), Duration.ofMillis(50),
                        Duration.ofSeconds(10), "contender-" + i))
                .toList();
        nodes.get(0).createTableIfMissing().block();

        List<Boolean> outcomes = Flux.fromIterable(nodes)
                .flatMap(node -> Mono.fromCallable(() -> node.obtain("contended").tryLock()).subscribeOn(Schedulers.boundedElastic()))
                .collectList().block(Duration.ofSeconds(20));

        assertThat(outcomes).hasSize(6);
        assertThat(outcomes.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        nodes.forEach(node -> node.obtain("contended").unlock());
        assertThat(nodes.get(0).holders().block()).doesNotContainKey("contended");
    }
}
