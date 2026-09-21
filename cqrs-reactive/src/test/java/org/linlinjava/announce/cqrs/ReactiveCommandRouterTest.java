package org.linlinjava.announce.cqrs;

import com.opencqrs.framework.command.Command;
import com.opencqrs.framework.command.CommandRouter;
import com.opencqrs.framework.command.CommandSubjectDoesNotExistException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReactiveCommandRouterTest {

    private record CreateGoods(String subject) implements Command {
        @Override
        public String getSubject() {
            return subject;
        }
    }

    @Test
    void sendEmitsHandlerResult() {
        CommandRouter router = mock(CommandRouter.class);
        Command command = new CreateGoods("/goods/1");
        when(router.<String>send(command)).thenReturn("created");

        StepVerifier.create(new ReactiveCommandRouter(router, Schedulers.immediate()).<String>send(command))
                .expectNext("created")
                .verifyComplete();
    }

    @Test
    void sendWithMetaDataForwardsMetaData() {
        CommandRouter router = mock(CommandRouter.class);
        Command command = new CreateGoods("/goods/1");
        Map<String, Object> metaData = Map.of("user", "42");
        when(router.<String>send(eq(command), eq(metaData))).thenReturn("created");

        StepVerifier.create(new ReactiveCommandRouter(router, Schedulers.immediate()).<String>send(command, metaData))
                .expectNext("created")
                .verifyComplete();
    }

    @Test
    void sendIsLazyUntilSubscribed() {
        CommandRouter router = mock(CommandRouter.class);
        Command command = new CreateGoods("/goods/1");

        new ReactiveCommandRouter(router, Schedulers.immediate()).send(command);

        verify(router, never()).send(any(Command.class));
    }

    @Test
    void handlerExceptionBecomesErrorSignal() {
        CommandRouter router = mock(CommandRouter.class);
        Command command = new CreateGoods("/goods/1");
        when(router.send(command)).thenThrow(Mockito.mock(CommandSubjectDoesNotExistException.class));

        StepVerifier.create(new ReactiveCommandRouter(router, Schedulers.immediate()).send(command))
                .expectError(CommandSubjectDoesNotExistException.class)
                .verify();
    }

    @Test
    void nullHandlerResultCompletesEmpty() {
        CommandRouter router = mock(CommandRouter.class);
        Command command = new CreateGoods("/goods/1");
        when(router.send(command)).thenReturn(null);

        StepVerifier.create(new ReactiveCommandRouter(router, Schedulers.immediate()).send(command))
                .verifyComplete();
    }
}
