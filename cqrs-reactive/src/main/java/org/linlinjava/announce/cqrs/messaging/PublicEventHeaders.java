package org.linlinjava.announce.cqrs.messaging;

/**
 * Kafka / Spring Messaging header names of a public event. Kafka headers are byte arrays on the wire; the
 * subscriber reads them as UTF-8 strings.
 */
public final class PublicEventHeaders {

    public static final String EVENT_TYPE = "event-type";
    public static final String EVENT_VERSION = "event-version";
    /** The EventSourcingDB id of the internal event this message was mapped from; use it to de-duplicate. */
    public static final String EVENT_ID = "event-id";
    /** The aggregate subject, also the Kafka record key so one aggregate's events stay in order. */
    public static final String SUBJECT = "subject";
    public static final String CORRELATION_ID = "correlation-id";
    /** The causation of the message: the internal event id. */
    public static final String CAUSATION_ID = "causation-id";
    public static final String PRODUCER = "producer";

    private PublicEventHeaders() {
    }
}
