package org.linlinjava.announce.messaging.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;

/**
 * The Micrometer meters of this service, in one place so their names stay stable across the tuning steps
 * described in {@code doc/messaging-architecture.md} §5. Every meter is prefixed {@code messaging.}; the
 * {@code node} tag comes from {@code management.metrics.tags.node}.
 *
 * <ul>
 *   <li>{@link #COMMAND_LATENCY}: time inside the command facade, from dispatch to answer, per command type
 *       and outcome ({@code ok}, {@code rejected}, {@code error}), retries included;</li>
 *   <li>{@link #COMMAND_RETRIES}: re-dispatches after the event store refused a write because another command
 *       changed the conversation first, per command type;</li>
 *   <li>{@link #LIVE_LATENCY}: end-to-end fan-out delay, from the event's own timestamp to the moment this
 *       node pushes it to its sockets, per record type;</li>
 *   <li>{@link #LIVE_DELIVERED}: frames pushed to local sockets from the live topic;</li>
 *   <li>{@link #PROJECTION_DELAY}: event written in EventSourcingDB to handled by the group on this node
 *       (the lag of the group, one sample per event);</li>
 *   <li>{@link #PROJECTION_HANDLER}: the handler's own duration, per group and outcome;</li>
 *   <li>{@link #SESSIONS}, {@link #SESSION_CONVERSATIONS}, {@link #STAFF_SESSIONS}: open sockets on this node;</li>
 *   <li>{@link #SINK_BUFFERED}, {@link #SINK_BUFFERED_MAX}, {@link #SINK_DROPPED}: frames waiting in the
 *       per-socket outbound buffers, and frames a buffer refused.</li>
 * </ul>
 */
public final class MessagingMeters {

    public static final String COMMAND_LATENCY = "messaging.command.latency";
    public static final String COMMAND_RETRIES = "messaging.command.retries";
    public static final String LIVE_LATENCY = "messaging.live.latency";
    public static final String LIVE_DELIVERED = "messaging.live.delivered";
    public static final String PROJECTION_DELAY = "messaging.projection.delay";
    public static final String PROJECTION_HANDLER = "messaging.projection.handler";
    public static final String SESSIONS = "messaging.sessions";
    public static final String SESSION_CONVERSATIONS = "messaging.sessions.conversations";
    public static final String STAFF_SESSIONS = "messaging.staff_sessions";
    public static final String SINK_BUFFERED = "messaging.sink.buffered";
    public static final String SINK_BUFFERED_MAX = "messaging.sink.buffered.max";
    public static final String SINK_DROPPED = "messaging.sink.dropped";

    public static final String TAG_COMMAND = "command";
    public static final String TAG_OUTCOME = "outcome";
    public static final String TAG_RECORD = "record";
    public static final String TAG_GROUP = "group";
    public static final String TAG_REASON = "reason";

    public static final String OUTCOME_OK = "ok";
    public static final String OUTCOME_REJECTED = "rejected";
    public static final String OUTCOME_ERROR = "error";

    private MessagingMeters() {
    }

    public static Timer commandLatency(MeterRegistry registry, String command, String outcome) {
        return Timer.builder(COMMAND_LATENCY).description("Command dispatch to answer, inside the messaging facade")
                .tag(TAG_COMMAND, command).tag(TAG_OUTCOME, outcome).register(registry);
    }

    public static Counter commandRetries(MeterRegistry registry, String command) {
        return Counter.builder(COMMAND_RETRIES).description("Re-dispatches after a concurrent write on the same conversation")
                .tag(TAG_COMMAND, command).register(registry);
    }

    public static Timer liveLatency(MeterRegistry registry, String record) {
        return Timer.builder(LIVE_LATENCY).description("Event time to push on this node's sockets (fan-out delay)")
                .tag(TAG_RECORD, record).register(registry);
    }

    public static Timer projectionDelay(MeterRegistry registry, String group) {
        return Timer.builder(PROJECTION_DELAY).description("Event written to handled by the group on this node")
                .tag(TAG_GROUP, group).register(registry);
    }

    public static Timer projectionHandler(MeterRegistry registry, String group, String outcome) {
        return Timer.builder(PROJECTION_HANDLER).description("Duration of one projection handler call")
                .tag(TAG_GROUP, group).tag(TAG_OUTCOME, outcome).register(registry);
    }

    /** A non-negative duration; clocks of the event writer and this node may disagree by a little. */
    public static Duration nonNegative(Duration d) {
        return d.isNegative() ? Duration.ZERO : d;
    }
}
