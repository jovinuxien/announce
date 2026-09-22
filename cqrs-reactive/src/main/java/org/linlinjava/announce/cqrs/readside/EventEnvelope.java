package org.linlinjava.announce.cqrs.readside;

import com.opencqrs.esdb.client.Event;
import org.linlinjava.announce.cqrs.observability.MetaDataKeys;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * What a projection knows about the event it handles besides the payload: where it came from and the metadata
 * the command side attached.
 *
 * @param subject  the aggregate subject, for example {@code /announce/42}
 * @param type     the EventSourcingDB event type (the class name by default)
 * @param eventId  the EventSourcingDB event id; strictly increasing in the log, usable for de-duplication
 * @param time     when the event was written
 * @param metaData the event metadata (correlation id, causation id, command id, and whatever the handler added)
 */
public record EventEnvelope(String subject, String type, String eventId, Instant time, Map<String, ?> metaData) {

    public EventEnvelope {
        metaData = metaData == null ? Map.of() : Map.copyOf(metaData);
    }

    public static EventEnvelope of(Event raw, Map<String, ?> metaData) {
        return new EventEnvelope(raw.subject(), raw.type(), raw.id(), raw.time(), metaData);
    }

    public Optional<String> correlationId() {
        return get(MetaDataKeys.CORRELATION_ID);
    }

    public Optional<String> causationId() {
        return get(MetaDataKeys.CAUSATION_ID);
    }

    public Optional<String> commandId() {
        return get(MetaDataKeys.COMMAND_ID);
    }

    public Optional<String> get(String key) {
        Object value = metaData.get(key);
        return value == null ? Optional.empty() : Optional.of(value.toString());
    }
}
