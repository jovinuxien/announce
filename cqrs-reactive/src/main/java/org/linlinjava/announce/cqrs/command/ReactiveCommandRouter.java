package org.linlinjava.announce.cqrs.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencqrs.framework.command.Command;
import com.opencqrs.framework.command.CommandRouter;
import org.linlinjava.announce.cqrs.observability.CorrelationContext;
import org.linlinjava.announce.cqrs.observability.MetaDataEnricher;
import org.linlinjava.announce.cqrs.observability.MetaDataKeys;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.Objects;

/**
 * Reactive facade over the blocking OpenCQRS {@link CommandRouter}, the Lagom {@code PersistentEntityRef.ask}
 * of this framework.
 *
 * <p>OpenCQRS talks to EventSourcingDB through a blocking HTTP client and runs command handlers synchronously.
 * Every dispatch is wrapped in {@link Mono#fromCallable} and subscribed on a bounded-elastic scheduler so the
 * blocking work never runs on a Netty thread.
 *
 * <p>On top of the plain dispatch this router:
 * <ul>
 *   <li>completes the command metadata through the {@link MetaDataEnricher} (correlation and causation ids);</li>
 *   <li>de-duplicates commands that carry a {@link MetaDataKeys#COMMAND_ID commandId} through the
 *       {@link CommandIdStore}: the id is claimed before the handler runs, the JSON result is stored after it,
 *       and the claim is released when the handler fails so the client may retry.</li>
 * </ul>
 * Handler exceptions are propagated as {@code Mono} error signals and mapped to problem details by
 * {@link CqrsProblemDetailsHandler}.
 */
public class ReactiveCommandRouter {

    private final CommandRouter commandRouter;
    private final Scheduler scheduler;
    private final MetaDataEnricher enricher;
    private final CommandIdStore commandIdStore;
    private final ObjectMapper objectMapper;

    public ReactiveCommandRouter(CommandRouter commandRouter) {
        this(commandRouter, Schedulers.boundedElastic(), MetaDataEnricher.identity(), null, null);
    }

    public ReactiveCommandRouter(CommandRouter commandRouter, Scheduler scheduler) {
        this(commandRouter, scheduler, MetaDataEnricher.identity(), null, null);
    }

    /**
     * @param commandRouter  the blocking OpenCQRS router
     * @param scheduler      where the blocking dispatch runs
     * @param enricher       completes command metadata before dispatch
     * @param commandIdStore command log for idempotent dispatch, or {@code null} to disable de-duplication
     * @param objectMapper   serialises handler results into the command log, required when a store is given
     */
    public ReactiveCommandRouter(CommandRouter commandRouter, Scheduler scheduler, MetaDataEnricher enricher,
                                 CommandIdStore commandIdStore, ObjectMapper objectMapper) {
        this.commandRouter = Objects.requireNonNull(commandRouter, "commandRouter");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.enricher = Objects.requireNonNull(enricher, "enricher");
        this.commandIdStore = commandIdStore;
        this.objectMapper = commandIdStore == null ? objectMapper : Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /** Dispatches the command with empty caller metadata. */
    public <R> Mono<R> send(Command command) {
        return send(command, Map.of(), null);
    }

    /**
     * Dispatches the command together with event metadata. A duplicate {@code commandId} fails with
     * {@link DuplicateCommandException} because the result type is unknown and cannot be replayed.
     */
    public <R> Mono<R> send(Command command, Map<String, ?> metaData) {
        return send(command, metaData, null);
    }

    /**
     * Dispatches the command; a duplicate {@code commandId} whose first dispatch completed replays the stored
     * result as {@code resultType} instead of running the handler again.
     *
     * @param resultType the handler result type, or {@code null} when duplicates should fail instead
     * @return a lazy {@code Mono} completing with the handler result, or empty when the handler returns {@code null}
     */
    public <R> Mono<R> send(Command command, Map<String, ?> metaData, Class<R> resultType) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(metaData, "metaData");
        return Mono.deferContextual(context -> {
            Map<String, Object> enriched = enricher.enrich(metaData, context);
            Object commandId = enriched.get(MetaDataKeys.COMMAND_ID);
            Mono<R> dispatch = dispatch(command, enriched);
            if (commandIdStore == null || commandId == null) {
                return dispatch;
            }
            return idempotent(String.valueOf(commandId), command.getSubject(), dispatch, resultType);
        });
    }

    private <R> Mono<R> dispatch(Command command, Map<String, Object> metaData) {
        Mono<R> dispatch = Mono.fromCallable(() -> commandRouter.<R>send(command, metaData)).subscribeOn(scheduler);
        Object correlationId = metaData.get(MetaDataKeys.CORRELATION_ID);
        return correlationId == null ? dispatch
                : dispatch.contextWrite(context -> CorrelationContext.with(context, correlationId.toString()));
    }

    private <R> Mono<R> idempotent(String commandId, String subject, Mono<R> dispatch, Class<R> resultType) {
        return commandIdStore.claim(commandId, subject).flatMap(claim -> {
            if (!claim.duplicate()) {
                return dispatch
                        .flatMap(result -> commandIdStore.complete(commandId, toJson(result)).thenReturn(result))
                        .switchIfEmpty(commandIdStore.complete(commandId, null).then(Mono.empty()))
                        .onErrorResume(error -> commandIdStore.release(commandId).then(Mono.error(error)));
            }
            if (claim.completed() && resultType != null) {
                return claim.resultJson() == null ? Mono.empty() : Mono.fromCallable(() -> fromJson(claim.resultJson(), resultType));
            }
            return Mono.error(new DuplicateCommandException(commandId, subject, !claim.completed()));
        });
    }

    private String toJson(Object result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise command result " + result.getClass().getName(), e);
        }
    }

    private <R> R fromJson(String json, Class<R> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot replay command result as " + type.getName(), e);
        }
    }
}
