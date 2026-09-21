package org.linlinjava.announce.cqrs;

import com.opencqrs.framework.command.Command;
import com.opencqrs.framework.command.CommandRouter;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.Objects;

/**
 * Reactive facade over the blocking OpenCQRS {@link CommandRouter}.
 *
 * <p>OpenCQRS talks to EventSourcingDB through a blocking HTTP client and runs
 * command handlers synchronously. Calling it directly from a WebFlux handler
 * would block an event-loop thread. This adapter wraps every dispatch in
 * {@link Mono#fromCallable(java.util.concurrent.Callable)} and moves the
 * subscription to a bounded-elastic scheduler so the blocking work never runs
 * on a Netty thread.
 *
 * <p>Handler exceptions (for example
 * {@link com.opencqrs.framework.command.CommandSubjectDoesNotExistException})
 * are propagated as {@code Mono} error signals.
 */
public class ReactiveCommandRouter {

    private final CommandRouter commandRouter;
    private final Scheduler scheduler;

    public ReactiveCommandRouter(CommandRouter commandRouter) {
        this(commandRouter, Schedulers.boundedElastic());
    }

    public ReactiveCommandRouter(CommandRouter commandRouter, Scheduler scheduler) {
        this.commandRouter = Objects.requireNonNull(commandRouter, "commandRouter");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * Dispatches the command on the blocking router and emits the handler result.
     *
     * @param command the command to dispatch
     * @param <R>     the handler result type
     * @return a lazy {@code Mono} completing with the handler result, or empty when the handler returns {@code null}
     */
    public <R> Mono<R> send(Command command) {
        Objects.requireNonNull(command, "command");
        return Mono.fromCallable(() -> commandRouter.<R>send(command))
                .subscribeOn(scheduler);
    }

    /**
     * Dispatches the command together with event metadata.
     *
     * @param command  the command to dispatch
     * @param metaData metadata attached to the events published by the handler
     * @param <R>      the handler result type
     * @return a lazy {@code Mono} completing with the handler result, or empty when the handler returns {@code null}
     */
    public <R> Mono<R> send(Command command, Map<String, ?> metaData) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(metaData, "metaData");
        return Mono.fromCallable(() -> commandRouter.<R>send(command, metaData))
                .subscribeOn(scheduler);
    }
}
