package org.linlinjava.announce.cqrs.readside;

import com.opencqrs.framework.eventhandler.EventHandlingProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Objects;

/**
 * Replays a projection from the beginning of the event log: stops the group's processors, clears the read
 * model ({@link ReactiveProjection#reset()}), drops the group's checkpoints and starts the processors again.
 *
 * <p>Run it on one instance only; the processors of other instances keep their old checkpoint until they
 * restart. For a brand-new projection no rebuild is needed: a group without a checkpoint starts from the
 * first event.
 */
public class ProjectionRebuilder {

    private static final Logger log = LoggerFactory.getLogger(ProjectionRebuilder.class);

    private final EventProcessors processors;
    private final List<ReactiveProjection> projections;
    private final List<ResettableProgressTracker> trackers;

    public ProjectionRebuilder(ApplicationContext context, List<ReactiveProjection> projections,
                               List<ResettableProgressTracker> trackers) {
        this(new EventProcessors(context), projections, trackers);
    }

    public ProjectionRebuilder(EventProcessors processors, List<ReactiveProjection> projections,
                               List<ResettableProgressTracker> trackers) {
        this.processors = Objects.requireNonNull(processors, "processors");
        this.projections = List.copyOf(projections);
        this.trackers = List.copyOf(trackers);
    }

    /** The groups of the registered projections. */
    public Flux<String> groups() {
        return Flux.fromIterable(projections).map(ReactiveProjection::group).distinct();
    }

    public Mono<Void> rebuild(String group) {
        List<ReactiveProjection> targets = projections.stream().filter(p -> p.group().equals(group)).toList();
        if (targets.isEmpty()) {
            return Mono.error(new IllegalArgumentException("No projection registered for group " + group));
        }
        if (trackers.isEmpty()) {
            return Mono.error(new IllegalStateException("No resettable progress tracker; configure cqrs.readside.progress.tracker=r2dbc"));
        }
        return Mono.fromRunnable(() -> stop(group))
                .then(Flux.fromIterable(targets).concatMap(ReactiveProjection::reset).then())
                .then(Flux.fromIterable(trackers).concatMap(tracker -> tracker.reset(group)).then())
                .doFinally(signal -> start(group))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void stop(String group) {
        for (EventHandlingProcessor processor : processors.processors(group)) {
            log.info("Stopping event processor {} partition {} for rebuild", group, processor.getPartition());
            processor.stop();
        }
    }

    private void start(String group) {
        for (EventHandlingProcessor processor : processors.processors(group)) {
            log.info("Starting event processor {} partition {} after rebuild", group, processor.getPartition());
            processor.start();
        }
    }
}
