package org.linlinjava.announce.cqrs.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencqrs.framework.command.Command;
import com.opencqrs.framework.command.CommandRouter;
import com.opencqrs.framework.command.CommandSubjectDoesNotExistException;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.cqrs.observability.CorrelationContext;
import org.linlinjava.announce.cqrs.observability.MetaDataEnricher;
import org.linlinjava.announce.cqrs.observability.MetaDataKeys;
import org.linlinjava.announce.cqrs.observability.TracingMetaDataEnricher;
import org.mockito.ArgumentCaptor;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReactiveCommandRouterTest {

    private record CreateAnnounce(String subject) implements Command {
        @Override
        public String getSubject() {
            return subject;
        }
    }

    private record Created(String id) {
    }

    private final CommandRouter router = mock(CommandRouter.class);
    private final Command command = new CreateAnnounce("/announce/1");

    private ReactiveCommandRouter plain() {
        return new ReactiveCommandRouter(router, Schedulers.immediate());
    }

    private ReactiveCommandRouter idempotent(CommandIdStore store) {
        return new ReactiveCommandRouter(router, Schedulers.immediate(), new TracingMetaDataEnricher(null), store, new ObjectMapper());
    }

    @Test
    void sendEmitsHandlerResult() {
        when(router.<String>send(eq(command), any())).thenReturn("created");

        StepVerifier.create(plain().<String>send(command)).expectNext("created").verifyComplete();
    }

    @Test
    void sendIsLazyUntilSubscribed() {
        plain().send(command);

        verify(router, never()).send(any(Command.class), any());
    }

    @Test
    void handlerExceptionBecomesErrorSignal() {
        when(router.send(eq(command), any())).thenThrow(new CommandSubjectDoesNotExistException("missing", command));

        StepVerifier.create(plain().send(command)).expectError(CommandSubjectDoesNotExistException.class).verify();
    }

    @Test
    void nullHandlerResultCompletesEmpty() {
        when(router.send(eq(command), any())).thenReturn(null);

        StepVerifier.create(plain().send(command)).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void enrichesMetaDataWithCorrelationFromContextAndCausationFromCommandId() {
        when(router.<String>send(eq(command), any())).thenReturn("ok");
        ReactiveCommandRouter reactive = idempotent(new InMemoryCommandIdStore());

        StepVerifier.create(reactive.<String>send(command, Map.of(MetaDataKeys.COMMAND_ID, "cmd-1"))
                        .contextWrite(context -> CorrelationContext.with(context, "corr-9")))
                .expectNext("ok").verifyComplete();

        ArgumentCaptor<Map<String, Object>> metaData = ArgumentCaptor.forClass(Map.class);
        verify(router).send(eq(command), metaData.capture());
        assertThat(metaData.getValue())
                .containsEntry(MetaDataKeys.COMMAND_ID, "cmd-1")
                .containsEntry(MetaDataKeys.CORRELATION_ID, "corr-9")
                .containsEntry(MetaDataKeys.CAUSATION_ID, "cmd-1");
    }

    @Test
    void generatesCorrelationIdWhenNoneIsKnown() {
        when(router.<String>send(eq(command), any())).thenReturn("ok");
        MetaDataEnricher enricher = new TracingMetaDataEnricher(null);

        assertThat(enricher.enrich(Map.of(), reactor.util.context.Context.empty()))
                .containsKey(MetaDataKeys.CORRELATION_ID)
                .doesNotContainKey(MetaDataKeys.CAUSATION_ID);
    }

    @Test
    void duplicateCommandIdReplaysStoredResultWhenTypeIsGiven() {
        when(router.<Created>send(eq(command), any())).thenReturn(new Created("1"));
        ReactiveCommandRouter reactive = idempotent(new InMemoryCommandIdStore());
        Map<String, ?> metaData = Map.of(MetaDataKeys.COMMAND_ID, "cmd-1");

        StepVerifier.create(reactive.send(command, metaData, Created.class)).expectNext(new Created("1")).verifyComplete();
        StepVerifier.create(reactive.send(command, metaData, Created.class)).expectNext(new Created("1")).verifyComplete();

        verify(router, times(1)).send(eq(command), any());
    }

    @Test
    void duplicateCommandIdFailsWhenNoTypeIsGiven() {
        when(router.<Created>send(eq(command), any())).thenReturn(new Created("1"));
        ReactiveCommandRouter reactive = idempotent(new InMemoryCommandIdStore());
        Map<String, ?> metaData = Map.of(MetaDataKeys.COMMAND_ID, "cmd-1");

        StepVerifier.create(reactive.send(command, metaData)).expectNextCount(1).verifyComplete();
        StepVerifier.create(reactive.send(command, metaData))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(DuplicateCommandException.class)
                        .hasMessageContaining("cmd-1").hasMessageContaining("already handled"))
                .verify();
    }

    @Test
    void failedDispatchReleasesTheClaimSoTheClientCanRetry() {
        AtomicInteger attempts = new AtomicInteger();
        when(router.<String>send(eq(command), any())).thenAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("event store hiccup");
            }
            return "ok";
        });
        InMemoryCommandIdStore store = new InMemoryCommandIdStore();
        ReactiveCommandRouter reactive = idempotent(store);
        Map<String, ?> metaData = Map.of(MetaDataKeys.COMMAND_ID, "cmd-1");

        StepVerifier.create(reactive.send(command, metaData, String.class)).expectError(IllegalStateException.class).verify();
        assertThat(store.size()).isZero();
        StepVerifier.create(reactive.send(command, metaData, String.class)).expectNext("ok").verifyComplete();
        assertThat(attempts).hasValue(2);
    }

    @Test
    void emptyResultIsRememberedAsCompleted() {
        when(router.send(eq(command), any())).thenReturn(null);
        ReactiveCommandRouter reactive = idempotent(new InMemoryCommandIdStore());
        Map<String, ?> metaData = Map.of(MetaDataKeys.COMMAND_ID, "cmd-1");

        StepVerifier.create(reactive.send(command, metaData, String.class)).verifyComplete();
        StepVerifier.create(reactive.send(command, metaData, String.class)).verifyComplete();
        verify(router, times(1)).send(eq(command), any());
    }

    @Test
    void commandsWithoutCommandIdAreNotDeduplicated() {
        when(router.<String>send(eq(command), any())).thenReturn("ok");
        ReactiveCommandRouter reactive = idempotent(new InMemoryCommandIdStore());

        StepVerifier.create(reactive.send(command, Map.of(), String.class)).expectNext("ok").verifyComplete();
        StepVerifier.create(reactive.send(command, Map.of(), String.class)).expectNext("ok").verifyComplete();
        verify(router, times(2)).send(eq(command), any());
    }
}
