package org.linlinjava.announce.cqrs.readside;

import com.opencqrs.framework.eventhandler.progress.Progress;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectionRebuilderTest {

    private final List<String> calls = new ArrayList<>();

    private final ReactiveProjection projection = new ReactiveProjection() {
        @Override
        public String group() {
            return "summary";
        }

        @Override
        public void configure(ProjectionHandlers handlers) {
        }

        @Override
        public Mono<Void> reset() {
            return Mono.fromRunnable(() -> calls.add("reset-read-model"));
        }
    };

    private final ResettableProgressTracker tracker = new ResettableProgressTracker() {
        @Override
        public Mono<Void> reset(String group) {
            return Mono.fromRunnable(() -> calls.add("reset-progress:" + group));
        }

        @Override
        public Progress current(String group, long partition) {
            return new Progress.None();
        }

        @Override
        public void proceed(String group, long partition, Supplier<Progress> execution) {
            execution.get();
        }
    };

    @Test
    void clearsReadModelThenCheckpoints() {
        ProjectionRebuilder rebuilder = new ProjectionRebuilder(new StaticApplicationContext(), List.of(projection), List.of(tracker));

        StepVerifier.create(rebuilder.rebuild("summary")).verifyComplete();
        StepVerifier.create(rebuilder.groups()).expectNext("summary").verifyComplete();

        assertThat(calls).containsExactly("reset-read-model", "reset-progress:summary");
    }

    @Test
    void unknownGroupAndMissingTrackerFail() {
        StepVerifier.create(new ProjectionRebuilder(new StaticApplicationContext(), List.of(projection), List.of(tracker)).rebuild("other"))
                .expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(new ProjectionRebuilder(new StaticApplicationContext(), List.of(projection), List.of()).rebuild("summary"))
                .expectError(IllegalStateException.class).verify();
    }
}
