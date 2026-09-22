package org.linlinjava.announce.cqrs.observability;

import reactor.util.context.ContextView;

import java.util.Map;

/**
 * Completes the metadata attached to a command before it is dispatched.
 */
@FunctionalInterface
public interface MetaDataEnricher {

    /**
     * @param given   the metadata supplied by the caller, never {@code null}
     * @param context the Reactor context of the dispatch
     * @return the metadata to attach to the command; the framework never mutates the given map
     */
    Map<String, Object> enrich(Map<String, ?> given, ContextView context);

    static MetaDataEnricher identity() {
        return (given, context) -> new java.util.LinkedHashMap<>(given);
    }
}
