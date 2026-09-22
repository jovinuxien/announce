package org.linlinjava.announce.cqrs.readside;

import com.opencqrs.framework.eventhandler.progress.ProgressTracker;
import reactor.core.publisher.Mono;

/**
 * A progress tracker whose checkpoints can be dropped so a group replays the log from the start.
 */
public interface ResettableProgressTracker extends ProgressTracker {

    /** Forgets every partition's checkpoint of the group. */
    Mono<Void> reset(String group);
}
