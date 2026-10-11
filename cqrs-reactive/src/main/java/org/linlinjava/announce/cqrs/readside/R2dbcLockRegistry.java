package org.linlinjava.announce.cqrs.readside;

import org.springframework.integration.support.locks.ExpirableLockRegistry;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * A Spring Integration {@link org.springframework.integration.support.locks.LockRegistry} over a MySQL table
 * reached through R2DBC, so that OpenCQRS's leader-election life-cycle controller can run on this stack
 * without JDBC, Redis or ZooKeeper.
 *
 * <p>One row per lock key holds the owner and a lease (<code>expires_at</code>). {@link Lock#tryLock()} takes
 * the row when it is free, expired, or already this registry's, and renews the lease in the same statement;
 * a row whose lease has run out may be taken by any other owner, which is how a crashed node loses its
 * groups. The lock is reentrant per registry (hold count), because {@code LockRegistryLeaderInitiator} calls
 * {@code tryLock} on every heartbeat while it is the leader and follows a successful call with one
 * {@code unlock}; the row is deleted only when the last hold is released.
 *
 * <p>Every call blocks on the database, bounded by {@code timeout}. Callers are the leader initiator's own
 * threads, never the event loop, exactly like {@link R2dbcProgressTracker}.
 */
public class R2dbcLockRegistry implements ExpirableLockRegistry {

    private final DatabaseClient client;
    private final String table;
    private final Duration ttl;
    private final Duration pollInterval;
    private final Duration timeout;
    private final String owner;
    private final ConcurrentMap<String, R2dbcLock> locks = new ConcurrentHashMap<>();

    public R2dbcLockRegistry(DatabaseClient client, String table, Duration ttl, Duration pollInterval, Duration timeout) {
        this(client, table, ttl, pollInterval, timeout, UUID.randomUUID().toString());
    }

    public R2dbcLockRegistry(DatabaseClient client, String table, Duration ttl, Duration pollInterval, Duration timeout, String owner) {
        this.client = Objects.requireNonNull(client, "client");
        this.table = Objects.requireNonNull(table, "table");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        this.pollInterval = Objects.requireNonNull(pollInterval, "pollInterval");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.owner = Objects.requireNonNull(owner, "owner");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }

    /** MySQL DDL of the lock table. */
    public static String ddl(String table) {
        return "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "lock_key VARCHAR(190) NOT NULL, "
                + "owner VARCHAR(64) NOT NULL, "
                + "expires_at TIMESTAMP(3) NOT NULL, "
                + "PRIMARY KEY (lock_key))";
    }

    public Mono<Void> createTableIfMissing() {
        return client.sql(ddl(table)).then();
    }

    /** The id this registry writes as the owner of the locks it holds (one per node). */
    public String owner() {
        return owner;
    }

    @Override
    public Lock obtain(Object lockKey) {
        return locks.computeIfAbsent(String.valueOf(lockKey), R2dbcLock::new);
    }

    /** Removes expired rows of any owner; the lease already makes them takeable, this only keeps the table small. */
    @Override
    public void expireUnusedOlderThan(long age) {
        client.sql("DELETE FROM " + table + " WHERE expires_at < DATE_SUB(NOW(3), INTERVAL :age MICROSECOND)")
                .bind("age", Math.max(0, age) * 1000)
                .fetch().rowsUpdated().block(timeout);
    }

    /** Current unexpired leases: lock key to owner. Diagnostics and tests. */
    public Mono<Map<String, String>> holders() {
        return client.sql("SELECT lock_key, owner FROM " + table + " WHERE expires_at > NOW(3) ORDER BY lock_key")
                .map((row, meta) -> Map.entry(row.get("lock_key", String.class), row.get("owner", String.class)))
                .all()
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()));
    }

    /**
     * One round trip that takes or renews the lease when the row is free, expired or already ours, and leaves
     * it alone otherwise; a second one reads back who owns it now. The assignments of the UPDATE part are
     * evaluated left to right, so the lease is refreshed exactly when the owner column ends up being us.
     */
    Mono<Boolean> acquire(String key) {
        long ttlMicros = ttl.toNanos() / 1000;
        return client.sql("INSERT INTO " + table + " (lock_key, owner, expires_at)"
                        + " VALUES (:k, :o1, DATE_ADD(NOW(3), INTERVAL :t1 MICROSECOND))"
                        + " ON DUPLICATE KEY UPDATE"
                        + " owner = IF(owner = :o2 OR expires_at < NOW(3), :o3, owner),"
                        + " expires_at = IF(owner = :o4, DATE_ADD(NOW(3), INTERVAL :t2 MICROSECOND), expires_at)")
                .bind("k", key).bind("o1", owner).bind("t1", ttlMicros)
                .bind("o2", owner).bind("o3", owner).bind("o4", owner).bind("t2", ttlMicros)
                .fetch().rowsUpdated()
                .then(client.sql("SELECT owner FROM " + table + " WHERE lock_key = :k")
                        .bind("k", key)
                        .map((row, meta) -> owner.equals(row.get("owner", String.class)))
                        .one()
                        .defaultIfEmpty(false));
    }

    Mono<Void> release(String key) {
        return client.sql("DELETE FROM " + table + " WHERE lock_key = :k AND owner = :o")
                .bind("k", key).bind("o", owner)
                .fetch().rowsUpdated().then();
    }

    final class R2dbcLock implements Lock {

        private final String key;
        private int holds;

        R2dbcLock(String key) {
            this.key = key;
        }

        @Override
        public void lock() {
            boolean interrupted = false;
            while (true) {
                try {
                    if (tryLock(pollInterval.toMillis(), TimeUnit.MILLISECONDS)) {
                        break;
                    }
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            while (!tryLock(pollInterval.toMillis(), TimeUnit.MILLISECONDS)) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
            }
        }

        @Override
        public synchronized boolean tryLock() {
            Boolean mine = acquire(key).block(timeout);
            if (Boolean.TRUE.equals(mine)) {
                holds++;
                return true;
            }
            holds = 0;
            return false;
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            long deadline = System.nanoTime() + unit.toNanos(time);
            while (true) {
                if (tryLock()) {
                    return true;
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                Thread.sleep(Math.min(pollInterval.toMillis(), TimeUnit.NANOSECONDS.toMillis(remaining) + 1));
            }
        }

        @Override
        public synchronized void unlock() {
            if (holds == 0) {
                return;
            }
            holds--;
            if (holds == 0) {
                release(key).block(timeout);
            }
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException("conditions are not supported by " + R2dbcLockRegistry.class.getSimpleName());
        }

        synchronized int holds() {
            return holds;
        }
    }
}
