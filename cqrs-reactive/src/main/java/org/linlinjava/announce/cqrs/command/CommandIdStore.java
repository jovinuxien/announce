package org.linlinjava.announce.cqrs.command;

import reactor.core.publisher.Mono;

/**
 * Remembers which command ids were handled so a retried command is not applied twice.
 *
 * <p>Protocol: {@link #claim} before dispatch; {@link #complete} after the handler succeeded (with its result
 * serialised to JSON, or {@code null}); {@link #release} when the handler failed so the client may retry.
 */
public interface CommandIdStore {

    /**
     * @return {@link Claim#fresh()} when this id was not seen before, otherwise a duplicate claim carrying the
     * stored result when the first dispatch already completed
     */
    Mono<Claim> claim(String commandId, String subject);

    Mono<Void> complete(String commandId, String resultJson);

    Mono<Void> release(String commandId);

    record Claim(boolean duplicate, boolean completed, String resultJson) {

        public static Claim fresh() {
            return new Claim(false, false, null);
        }

        public static Claim inFlight() {
            return new Claim(true, false, null);
        }

        public static Claim completed(String resultJson) {
            return new Claim(true, true, resultJson);
        }
    }
}
