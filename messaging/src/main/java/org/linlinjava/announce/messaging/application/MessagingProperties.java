package org.linlinjava.announce.messaging.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Settings of the messaging service, under the {@code messaging} prefix. */
@ConfigurationProperties(prefix = "messaging")
public class MessagingProperties {

    /** How many characters a TEXT message may hold. */
    private int maxTextLength = 4000;
    /** How many messages one history page returns at most. */
    private int maxPageSize = 200;
    /** What happens when two commands race on one conversation. */
    private final CommandRetry commandRetry = new CommandRetry();
    /** How long a socket handshake waits for the read model. */
    private final Attach attach = new Attach();

    public int getMaxTextLength() {
        return maxTextLength;
    }

    public void setMaxTextLength(int maxTextLength) {
        this.maxTextLength = maxTextLength;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    public CommandRetry getCommandRetry() {
        return commandRetry;
    }

    public Attach getAttach() {
        return attach;
    }

    /**
     * Two commands on one conversation that are handled at the same moment both read the same state; the event
     * store accepts the first write and refuses the second (a {@code ConcurrencyException}, nothing is written).
     * The refused command is dispatched again on the new state, at most {@link #getMaxRetries()} times.
     */
    public static class CommandRetry {

        /** Re-dispatches after the first attempt; 0 turns the retry off. */
        private int maxRetries = 3;
        /** Delay before the first re-dispatch; doubles per retry, with jitter. */
        private Duration minBackoff = Duration.ofMillis(5);
        /** Upper bound of the delay between two attempts. */
        private Duration maxBackoff = Duration.ofMillis(50);

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public Duration getMinBackoff() {
            return minBackoff;
        }

        public void setMinBackoff(Duration minBackoff) {
            this.minBackoff = minBackoff;
        }

        public Duration getMaxBackoff() {
            return maxBackoff;
        }

        public void setMaxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
        }
    }

    /**
     * The socket handshake authorises against the read model, which trails the command that opened the
     * conversation by the projection delay. A conversation the read model does not know yet is looked up again
     * {@link #getRechecks()} times, {@link #getRecheckInterval()} apart, before the socket is refused.
     */
    public static class Attach {

        /** Further look-ups after the first one; 0 refuses at once. */
        private int rechecks = 5;
        /** Pause between two look-ups. */
        private Duration recheckInterval = Duration.ofMillis(200);

        public int getRechecks() {
            return rechecks;
        }

        public void setRechecks(int rechecks) {
            this.rechecks = rechecks;
        }

        public Duration getRecheckInterval() {
            return recheckInterval;
        }

        public void setRecheckInterval(Duration recheckInterval) {
            this.recheckInterval = recheckInterval;
        }
    }
}
