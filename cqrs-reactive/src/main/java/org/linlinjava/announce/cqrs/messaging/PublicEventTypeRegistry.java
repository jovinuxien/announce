package org.linlinjava.announce.cqrs.messaging;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves {@code (type, version)} header pairs to {@link PublicEvent} classes and back.
 */
public final class PublicEventTypeRegistry {

    public record Key(String type, int version) {
    }

    private final Map<Key, Class<?>> byKey = new LinkedHashMap<>();
    private final Map<Class<?>, Key> byClass = new LinkedHashMap<>();

    public PublicEventTypeRegistry register(Class<?>... eventClasses) {
        for (Class<?> eventClass : eventClasses) {
            Key key = keyOf(eventClass);
            Class<?> previous = byKey.putIfAbsent(key, eventClass);
            if (previous != null && previous != eventClass) {
                throw new IllegalArgumentException(key + " is already bound to " + previous.getName());
            }
            byClass.put(eventClass, key);
        }
        return this;
    }

    public Optional<Class<?>> resolve(String type, int version) {
        return Optional.ofNullable(byKey.get(new Key(type, version)));
    }

    public Key keyFor(Class<?> eventClass) {
        Key key = byClass.get(eventClass);
        return key != null ? key : keyOf(eventClass);
    }

    public static Key keyOf(Class<?> eventClass) {
        PublicEvent annotation = Objects.requireNonNull(eventClass, "eventClass").getAnnotation(PublicEvent.class);
        if (annotation == null) {
            throw new IllegalArgumentException(eventClass.getName() + " is not annotated with @PublicEvent");
        }
        return new Key(annotation.type(), annotation.version());
    }
}
