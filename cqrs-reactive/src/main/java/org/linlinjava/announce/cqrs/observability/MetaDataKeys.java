package org.linlinjava.announce.cqrs.observability;

/**
 * Event metadata keys the framework writes and propagates. The same names are used as Kafka headers
 * (see {@code PublicEventHeaders}) so a message can be traced back to the command that caused it.
 */
public final class MetaDataKeys {

    /** Identifies one business interaction across services; set on the first command and kept on everything after. */
    public static final String CORRELATION_ID = "correlationId";
    /** Identifies what caused this event or message: the command id for events, the event id for public messages. */
    public static final String CAUSATION_ID = "causationId";
    /** Client-supplied command id used for idempotent dispatch. */
    public static final String COMMAND_ID = "commandId";

    private MetaDataKeys() {
    }
}
