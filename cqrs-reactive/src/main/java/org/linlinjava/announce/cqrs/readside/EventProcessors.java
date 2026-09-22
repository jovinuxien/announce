package org.linlinjava.announce.cqrs.readside;

import com.opencqrs.framework.eventhandler.EventHandlingProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.support.GenericApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Finds the OpenCQRS {@link EventHandlingProcessor}s of this service (OpenCQRS creates them in a child
 * application context) and stops them all when the service shuts down, before the R2DBC connection pool
 * and the Kafka binder are destroyed. Without this the processors keep running into a closed pool and log
 * spurious "giving up" errors during shutdown.
 */
public class EventProcessors implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(EventProcessors.class);

    private final ApplicationContext context;
    private final AtomicBoolean running = new AtomicBoolean();

    public EventProcessors(ApplicationContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    /** Every processor, or only those of {@code group} when it is not {@code null}. */
    public List<EventHandlingProcessor> processors(String group) {
        List<EventHandlingProcessor> result = new ArrayList<>();
        context.getBeansOfType(EventHandlingProcessor.class).values().stream()
                .filter(p -> group == null || group.equals(p.getGroupId())).forEach(result::add);
        for (GenericApplicationContext child : context.getBeansOfType(GenericApplicationContext.class).values()) {
            child.getBeansOfType(EventHandlingProcessor.class).values().stream()
                    .filter(p -> group == null || group.equals(p.getGroupId())).forEach(result::add);
        }
        return result;
    }

    @Override
    public void start() {
        running.set(true);
    }

    @Override
    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        for (EventHandlingProcessor processor : processors(null)) {
            log.debug("Stopping event processor {} partition {} on shutdown", processor.getGroupId(), processor.getPartition());
            try {
                processor.stop();
            } catch (RuntimeException e) {
                log.warn("Event processor {} partition {} failed to stop: {}", processor.getGroupId(), processor.getPartition(), e.toString());
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /** Stopped first on shutdown, after everything else has started. */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
