package org.linlinjava.announce.messaging.infrastructure.live;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LiveSinkTest {

    @Test
    void aBoundedSinkRefusesBeyondItsCapacityAndDeliversWhatItAcceptedThenCompletes() {
        AtomicInteger refused = new AtomicInteger();
        LiveSink sink = new LiveSink(64, result -> refused.incrementAndGet());
        List<String> received = new ArrayList<>();
        BaseSubscriber<String> subscriber = new BaseSubscriber<>() {
            @Override
            protected void hookOnSubscribe(org.reactivestreams.Subscription subscription) {
                request(100);                       // a consumer that takes 100 then stalls
            }

            @Override
            protected void hookOnNext(String value) {
                received.add(value);
            }
        };
        sink.frames().subscribe(subscriber);
        for (int i = 0; i < 4000; i++) {
            sink.emit("frame " + i);
        }
        assertThat(received).hasSize(100);
        assertThat(sink.buffered()).isEqualTo(64);
        assertThat(refused.get()).isEqualTo(4000 - 164);
        assertThat(sink.overflowed()).isTrue();

        subscriber.request(Long.MAX_VALUE);       // the consumer resumes
        assertThat(received).hasSize(164);
        assertThat(received.get(163)).isEqualTo("frame 163");
        assertThat(sink.buffered()).isZero();
    }

    @Test
    void mergedWithPingsTheBoundedSinkStillDeliversEverythingItAccepted() {
        AtomicInteger refused = new AtomicInteger();
        LiveSink sink = new LiveSink(64, result -> refused.incrementAndGet());
        Sinks.Empty<Void> done = Sinks.empty();
        Flux<String> frames = sink.frames().doFinally(s -> done.tryEmitEmpty());
        Flux<String> pings = Flux.never();
        List<String> received = new ArrayList<>();
        BaseSubscriber<String> subscriber = new BaseSubscriber<>() {
            @Override
            protected void hookOnSubscribe(org.reactivestreams.Subscription subscription) {
                request(100);
            }

            @Override
            protected void hookOnNext(String value) {
                received.add(value);
            }
        };
        Flux.merge(frames, pings.takeUntilOther(done.asMono())).subscribe(subscriber);
        for (int i = 0; i < 4000; i++) {
            sink.emit("frame " + i);
        }
        int accepted = 4000 - refused.get();
        subscriber.request(Long.MAX_VALUE);
        assertThat(received).as("accepted " + accepted).hasSize(accepted);
    }
}
