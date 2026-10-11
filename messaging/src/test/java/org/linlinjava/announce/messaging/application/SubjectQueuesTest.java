package org.linlinjava.announce.messaging.application;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One command per subject at a time on this node; other subjects are not held up; a failed or cancelled command
 * releases its subject; the map holds only subjects with work in flight.
 */
class SubjectQueuesTest {

    final List<String> waited = new CopyOnWriteArrayList<>();
    final SubjectQueues queues = new SubjectQueues(waited::add);
    final List<String> log = new CopyOnWriteArrayList<>();

    /** A command that records when it starts and finishes and completes when told to. */
    Mono<String> command(String name, Sinks.Empty<Void> finish) {
        return Mono.defer(() -> {
            log.add(name + " starts");
            return finish.asMono().then(Mono.fromCallable(() -> {
                log.add(name + " ends");
                return name;
            }));
        });
    }

    @Test
    void aSecondCommandOfTheSameSubjectWaitsForTheFirstWhileAnotherSubjectRunsAtOnce() {
        Sinks.Empty<Void> firstDone = Sinks.empty();
        Sinks.Empty<Void> secondDone = Sinks.empty();
        Sinks.Empty<Void> otherDone = Sinks.empty();
        AtomicInteger results = new AtomicInteger();

        queues.serialize("/conversation/a", command("a1", firstDone)).subscribe(r -> results.incrementAndGet());
        queues.serialize("/conversation/a", command("a2", secondDone)).subscribe(r -> results.incrementAndGet());
        queues.serialize("/conversation/b", command("b1", otherDone)).subscribe(r -> results.incrementAndGet());

        assertThat(log).containsExactly("a1 starts", "b1 starts");
        assertThat(waited).containsExactly("/conversation/a");
        assertThat(queues.size()).isEqualTo(2);

        firstDone.tryEmitEmpty();
        assertThat(log).containsExactly("a1 starts", "b1 starts", "a1 ends", "a2 starts");
        secondDone.tryEmitEmpty();
        otherDone.tryEmitEmpty();
        assertThat(log).containsExactly("a1 starts", "b1 starts", "a1 ends", "a2 starts", "a2 ends", "b1 ends");
        assertThat(results.get()).isEqualTo(3);
        assertThat(queues.size()).as("nothing in flight, nothing kept").isZero();
    }

    @Test
    void aFailedCommandReleasesItsSubjectAndTheNextOneRuns() {
        Mono<String> failing = Mono.error(new IllegalStateException("store refused"));
        Sinks.Empty<Void> nextDone = Sinks.empty();

        StepVerifier.create(queues.serialize("/conversation/a", failing)).expectError(IllegalStateException.class).verify(Duration.ofSeconds(5));
        queues.serialize("/conversation/a", command("a2", nextDone)).subscribe();

        assertThat(log).containsExactly("a2 starts");
        assertThat(waited).as("the failed one had finished, so no wait").isEmpty();
        nextDone.tryEmitEmpty();
        assertThat(queues.size()).isZero();
    }

    @Test
    void aCancelledCommandReleasesItsSubject() {
        Sinks.Empty<Void> never = Sinks.empty();
        Sinks.Empty<Void> nextDone = Sinks.empty();
        var first = queues.serialize("/conversation/a", command("a1", never)).subscribe();
        queues.serialize("/conversation/a", command("a2", nextDone)).subscribe();
        assertThat(log).containsExactly("a1 starts");

        first.dispose();

        assertThat(log).containsExactly("a1 starts", "a2 starts");
        nextDone.tryEmitEmpty();
        assertThat(queues.size()).isZero();
    }

    @Test
    void manyConcurrentCommandsOfOneSubjectNeverOverlap() {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger overlaps = new AtomicInteger();
        AtomicInteger done = new AtomicInteger();
        Mono<Integer> command = Mono.fromCallable(() -> {
            if (inFlight.incrementAndGet() > 1) {
                overlaps.incrementAndGet();
            }
            Thread.sleep(2);
            inFlight.decrementAndGet();
            return done.incrementAndGet();
        }).subscribeOn(Schedulers.boundedElastic());

        Mono.when(java.util.stream.IntStream.range(0, 50).mapToObj(i -> queues.serialize("/conversation/x", command)).toList())
                .block(Duration.ofSeconds(30));

        assertThat(done.get()).isEqualTo(50);
        assertThat(overlaps.get()).as("never two commands of the subject at once").isZero();
        assertThat(queues.size()).isZero();
    }
}
