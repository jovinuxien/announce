package org.linlinjava.announce.cqrs.command;

import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Command log kept in memory. Fine for tests and single-instance development; a restart forgets every id.
 */
public class InMemoryCommandIdStore implements CommandIdStore {

    private record Entry(boolean completed, Optional<String> resultJson) {
    }

    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public Mono<Claim> claim(String commandId, String subject) {
        return Mono.fromSupplier(() -> {
            Entry existing = entries.putIfAbsent(commandId, new Entry(false, Optional.empty()));
            if (existing == null) {
                return Claim.fresh();
            }
            return existing.completed() ? Claim.completed(existing.resultJson().orElse(null)) : Claim.inFlight();
        });
    }

    @Override
    public Mono<Void> complete(String commandId, String resultJson) {
        return Mono.fromRunnable(() -> entries.put(commandId, new Entry(true, Optional.ofNullable(resultJson))));
    }

    @Override
    public Mono<Void> release(String commandId) {
        return Mono.fromRunnable(() -> entries.remove(commandId));
    }

    public int size() {
        return entries.size();
    }
}
