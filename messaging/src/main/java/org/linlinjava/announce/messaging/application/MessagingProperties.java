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
    /** What protects a node from a socket whose client reads slowly, not at all, or is gone. */
    private final Socket socket = new Socket();

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

    /**
     * One command per conversation in flight on this node (a second one waits for the first): removes the
     * refused writes and re-dispatches of two participants posting at the same moment on the same node.
     */
    private boolean serializeCommands = true;

    public boolean isSerializeCommands() {
        return serializeCommands;
    }

    public void setSerializeCommands(boolean serializeCommands) {
        this.serializeCommands = serializeCommands;
    }

    public CommandRetry getCommandRetry() {
        return commandRetry;
    }

    public Attach getAttach() {
        return attach;
    }

    public Socket getSocket() {
        return socket;
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

    /**
     * Every WebSocket session queues the frames it has to push until the client's TCP connection takes them. A
     * client that reads slowly or not at all would otherwise make the node queue frames without bound and keep
     * the session forever. Tuning step 5 of {@code doc/messaging-architecture.md}.
     */
    public static class Socket {

        /**
         * Frames queued for one socket before it is treated as a slow consumer: the frames it holds are still
         * delivered, then it is closed with 4408 and the client catches up over {@code GET .../messages?after=}.
         * Rounded up to a power of two; 0 keeps the queue unbounded.
         */
        private int bufferFrames = 256;
        /** A protocol ping is sent to every socket at this interval; clients answer it without application code. Zero sends none. */
        private Duration pingInterval = Duration.ofSeconds(30);
        /** A socket from which no frame (the pong included) arrived for this long is closed with 4409. Zero never closes. */
        private Duration idleTimeout = Duration.ofSeconds(75);
        /**
         * A frame the kernel could not take within this time (the client's connection stopped reading) ends the
         * connection; this is what reclaims a socket whose peer is gone without a close. Applies to every
         * connection of the server. Zero turns it off.
         */
        private Duration writeTimeout = Duration.ofSeconds(30);

        public int getBufferFrames() {
            return bufferFrames;
        }

        public void setBufferFrames(int bufferFrames) {
            this.bufferFrames = bufferFrames;
        }

        public Duration getPingInterval() {
            return pingInterval;
        }

        public void setPingInterval(Duration pingInterval) {
            this.pingInterval = pingInterval;
        }

        public Duration getIdleTimeout() {
            return idleTimeout;
        }

        public void setIdleTimeout(Duration idleTimeout) {
            this.idleTimeout = idleTimeout;
        }

        public Duration getWriteTimeout() {
            return writeTimeout;
        }

        public void setWriteTimeout(Duration writeTimeout) {
            this.writeTimeout = writeTimeout;
        }
    }
}
