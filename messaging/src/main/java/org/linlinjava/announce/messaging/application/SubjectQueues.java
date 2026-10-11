package org.linlinjava.announce.messaging.application;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * At most one command per subject in flight on this node: a command for a subject that already has one running
 * waits until that one is over (success, failure or cancellation), then runs in its turn. Commands of different
 * subjects do not wait for each other.
 *
 * <p>Why: two commands racing on one conversation cost a refused write in EventSourcingDB and a re-dispatch each
 * time (tuning step 8 of {@code doc/messaging-architecture.md}); under load, when every write waits in the
 * store's queue, the retry lands in the same window as the peer's next command and collides again until the
 * retries are exhausted. Both participants of a conversation usually talk to the same node, so a queue per
 * subject on the node removes those collisions at no cost to other conversations; the retry of
 * {@link Conversations} stays for the commands of other nodes.
 *
 * <p>The queue is a chain of monos: each command keeps a signal that it is done, the next command for the same
 * subject subscribes to it first. The map holds only subjects with a command in flight or waiting.
 */
public final class SubjectQueues {

    private final ConcurrentHashMap<String, Mono<Void>> tails = new ConcurrentHashMap<>();
    private final Consumer<String> onWait;

    public SubjectQueues() {
        this(subject -> {
        });
    }

    /** @param onWait called with the subject whenever a command had to wait for an earlier one */
    public SubjectQueues(Consumer<String> onWait) {
        this.onWait = onWait;
    }

    /** {@code command}, run after every command of the same subject that was submitted before it. */
    public <R> Mono<R> serialize(String subject, Mono<R> command) {
        return serialize(subject, command, () -> {
        });
    }

    /** Same, with {@code waited} called (besides the constructor's consumer) when the command had to wait. */
    public <R> Mono<R> serialize(String subject, Mono<R> command, Runnable waited) {
        return Mono.defer(() -> {
            Sinks.Empty<Void> done = Sinks.empty();
            Mono<Void> mine = done.asMono();
            Mono<Void> previous = tails.put(subject, mine);
            Mono<R> turn;
            if (previous == null) {
                turn = command;
            } else {
                onWait.accept(subject);
                waited.run();
                turn = previous.then(command);
            }
            return turn.doFinally(signal -> {
                done.tryEmitEmpty();
                tails.remove(subject, mine);
            });
        });
    }

    /** Subjects with a command in flight or waiting, for tests and gauges. */
    public int size() {
        return tails.size();
    }
}
