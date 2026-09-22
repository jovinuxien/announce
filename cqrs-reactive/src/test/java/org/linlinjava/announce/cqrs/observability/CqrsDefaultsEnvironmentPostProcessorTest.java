package org.linlinjava.announce.cqrs.observability;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class CqrsDefaultsEnvironmentPostProcessorTest {

    @Test
    void addsLowestPriorityDefaults() {
        MockEnvironment environment = new MockEnvironment().withProperty("opencqrs.metadata.propagation.mode", "NONE");
        new CqrsDefaultsEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("opencqrs.metadata.propagation.mode")).isEqualTo("NONE");
        assertThat(environment.getProperty("opencqrs.metadata.propagation.keys")).isEqualTo("correlationId,causationId,commandId");
        assertThat(environment.getProperty("opencqrs.event-handling.standard.progress.tracker-ref")).isEqualTo("r2dbcProgressTracker");
    }

    @Test
    void inMemoryTrackerLeavesOpenCqrsDefaultTracker() {
        MockEnvironment environment = new MockEnvironment().withProperty("cqrs.readside.progress.tracker", "in-memory");
        new CqrsDefaultsEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("opencqrs.event-handling.standard.progress.tracker-ref")).isNull();
    }
}
