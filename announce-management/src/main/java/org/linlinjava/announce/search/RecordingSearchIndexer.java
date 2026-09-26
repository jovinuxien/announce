package org.linlinjava.announce.search;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link SearchIndexer} for {@code announce.search.mode=noop}: nothing leaves the process, every call is remembered
 * so a test can assert what would have reached the index. Also usable for a local run without the search tier.
 */
@Component
@ConditionalOnProperty(prefix = "announce.search", name = "mode", havingValue = "noop")
public class RecordingSearchIndexer implements SearchIndexer {

    /** One recorded {@code index} call. */
    public record Indexed(String id, Map<String, Object> data) {
    }

    private final List<Indexed> indexed = new CopyOnWriteArrayList<>();
    private final List<String> removed = new CopyOnWriteArrayList<>();

    @Override
    public Mono<Void> index(String id, Map<String, Object> data) {
        return Mono.fromRunnable(() -> indexed.add(new Indexed(id, Collections.unmodifiableMap(new LinkedHashMap<>(data)))));
    }

    @Override
    public Mono<Void> remove(String id) {
        return Mono.fromRunnable(() -> removed.add(id));
    }

    /** Every document put, in call order (the same id may appear several times). */
    public List<Indexed> indexed() {
        return List.copyOf(indexed);
    }

    /** Every id removed, in call order. */
    public List<String> removed() {
        return List.copyOf(removed);
    }

    public long indexCount(String id) {
        return indexed.stream().filter(d -> d.id().equals(id)).count();
    }

    public void clear() {
        indexed.clear();
        removed.clear();
    }
}
