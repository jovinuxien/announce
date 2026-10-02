package org.linlinjava.announce.messaging.application;

import com.opencqrs.framework.client.ConcurrencyException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.cqrs.command.ReactiveCommandRouter;
import org.linlinjava.announce.messaging.domain.conversation.ContentType;
import org.linlinjava.announce.messaging.domain.conversation.PostResult;
import org.linlinjava.announce.messaging.domain.conversation.Sender;
import org.linlinjava.announce.messaging.infrastructure.metrics.MessagingMeters;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What the command facade does when the event store refuses a write because another command changed the
 * conversation first: dispatch again, a bounded number of times, and only for that failure.
 */
class ConversationsRetryTest {

    static final PostResult POSTED = new PostResult("c1", "m1", 3, "k1", false, LocalDateTime.of(2026, 10, 1, 12, 0));

    final ReactiveCommandRouter router = mock(ReactiveCommandRouter.class);
    final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    final AtomicInteger dispatches = new AtomicInteger();

    Conversations conversations(int maxRetries) {
        MessagingProperties properties = new MessagingProperties();
        properties.getCommandRetry().setMaxRetries(maxRetries);
        properties.getCommandRetry().setMinBackoff(Duration.ofMillis(1));
        properties.getCommandRetry().setMaxBackoff(Duration.ofMillis(2));
        return new Conversations(router, null, null, Clock.systemUTC(), meters, properties);
    }

    /** The router answers with a lazy Mono; every subscription is one dispatch, the first {@code failures} lose the race. */
    void routerFails(int failures, Throwable failure) {
        when(router.send(any(), anyMap(), eq(PostResult.class))).thenReturn(Mono.defer(() ->
                dispatches.incrementAndGet() <= failures ? Mono.error(failure) : Mono.just(POSTED)));
    }

    Mono<PostResult> post(Conversations conversations) {
        return conversations.post("c1", Sender.user(7), ContentType.TEXT, "hello", null, null, null, "k1", null);
    }

    static ConcurrencyException lostTheRace() {
        return new ConcurrencyException("the subject changed", null);
    }

    double retries() {
        return MessagingMeters.commandRetries(meters, "PostMessage").count();
    }

    long latencyCount(String outcome) {
        return MessagingMeters.commandLatency(meters, "PostMessage", outcome).count();
    }

    @Test
    void aPostThatLosesTheRaceIsDispatchedAgainAndAnswersOnce() {
        routerFails(2, lostTheRace());
        StepVerifier.create(post(conversations(3))).expectNext(POSTED).verifyComplete();
        assertThat(dispatches).hasValue(3);
        assertThat(retries()).isEqualTo(2);
        assertThat(latencyCount(MessagingMeters.OUTCOME_OK)).as("one timing for the whole dispatch, retries included").isEqualTo(1);
        assertThat(latencyCount(MessagingMeters.OUTCOME_ERROR)).isZero();
    }

    @Test
    void theRetryIsBoundedAndThenFailsWithTheConcurrencyException() {
        routerFails(Integer.MAX_VALUE, lostTheRace());
        StepVerifier.create(post(conversations(3))).expectError(ConcurrencyException.class).verify();
        assertThat(dispatches).as("the first attempt and three retries").hasValue(4);
        assertThat(retries()).isEqualTo(3);
        assertThat(latencyCount(MessagingMeters.OUTCOME_ERROR)).isEqualTo(1);
    }

    @Test
    void aRejectionIsNotRetried() {
        routerFails(Integer.MAX_VALUE, new CommandRejectedException(HttpStatus.FORBIDDEN, "not-participant", "no"));
        StepVerifier.create(post(conversations(3))).expectError(CommandRejectedException.class).verify();
        assertThat(dispatches).hasValue(1);
        assertThat(retries()).isZero();
        assertThat(latencyCount(MessagingMeters.OUTCOME_REJECTED)).isEqualTo(1);
    }

    @Test
    void zeroRetriesTurnsItOff() {
        routerFails(1, lostTheRace());
        StepVerifier.create(post(conversations(0))).expectError(ConcurrencyException.class).verify();
        assertThat(dispatches).hasValue(1);
    }
}
