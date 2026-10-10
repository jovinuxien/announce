package org.linlinjava.announce.cqrs;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Settings of the reactive CQRS framework, all under the {@code cqrs} prefix.
 */
@ConfigurationProperties(prefix = "cqrs")
public class CqrsProperties {

    private final Command command = new Command();
    private final ReadSide readside = new ReadSide();
    private final Messaging messaging = new Messaging();
    private final Api api = new Api();
    private final Observability observability = new Observability();

    public Command getCommand() {
        return command;
    }

    public ReadSide getReadside() {
        return readside;
    }

    public Messaging getMessaging() {
        return messaging;
    }

    public Api getApi() {
        return api;
    }

    public Observability getObservability() {
        return observability;
    }

    public static class Command {
        private final Idempotency idempotency = new Idempotency();

        public Idempotency getIdempotency() {
            return idempotency;
        }

        public static class Idempotency {
            /** Whether commands carrying a {@code commandId} in their metadata are de-duplicated. */
            private boolean enabled = true;
            /** Whether the command log table is created on start-up when missing (MySQL DDL). */
            private boolean createTable = true;
            /** Name of the command log table. */
            private String table = "cqrs_command_log";

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public boolean isCreateTable() {
                return createTable;
            }

            public void setCreateTable(boolean createTable) {
                this.createTable = createTable;
            }

            public String getTable() {
                return table;
            }

            public void setTable(String table) {
                this.table = table;
            }
        }
    }

    public static class ReadSide {
        /** How long a projection handler may take for one event before the processor treats it as failed. */
        private Duration handlerTimeout = Duration.ofSeconds(30);
        private final Progress progress = new Progress();

        public Duration getHandlerTimeout() {
            return handlerTimeout;
        }

        public void setHandlerTimeout(Duration handlerTimeout) {
            this.handlerTimeout = handlerTimeout;
        }

        public Progress getProgress() {
            return progress;
        }

        public static class Progress {
            /** Which checkpoint store the event handling processors use. */
            private Tracker tracker = Tracker.R2DBC;
            /** Whether the progress table is created on start-up when missing (MySQL DDL). */
            private boolean createTable = true;
            /** Name of the progress (checkpoint) table. */
            private String table = "cqrs_readside_progress";

            public Tracker getTracker() {
                return tracker;
            }

            public void setTracker(Tracker tracker) {
                this.tracker = tracker;
            }

            public boolean isCreateTable() {
                return createTable;
            }

            public void setCreateTable(boolean createTable) {
                this.createTable = createTable;
            }

            public String getTable() {
                return table;
            }

            public void setTable(String table) {
                this.table = table;
            }

            public enum Tracker {
                /** Checkpoints in MySQL next to the read model (bean {@code r2dbcProgressTracker}). */
                R2DBC,
                /** OpenCQRS' in-memory tracker: every start replays from the beginning. Tests only. */
                IN_MEMORY
            }
        }
    }

    public static class Messaging {
        /** How long a subscriber handler may take for one record before the binder sees a failure. */
        private Duration handlerTimeout = Duration.ofSeconds(30);

        public Duration getHandlerTimeout() {
            return handlerTimeout;
        }

        public void setHandlerTimeout(Duration handlerTimeout) {
            this.handlerTimeout = handlerTimeout;
        }
    }

    public static class Api {
        /** Per service-id client overrides, keyed by the id passed to {@code HttpExchangeClients.create}. */
        private final Map<String, Client> clients = new LinkedHashMap<>();

        public Map<String, Client> getClients() {
            return clients;
        }

        public static class Client {
            /** Fixed base URL; when set, service discovery and load balancing are bypassed for this client. */
            private String baseUrl;
            /**
             * Whether calls of this client carry {@code X-Service-Token} (needed when the provider runs with
             * {@code announce.service-token.mode=enforce}). Off by default so the secret-derived token never
             * reaches a service that is not ours, such as the OCS search tier.
             */
            private boolean serviceToken;

            public String getBaseUrl() {
                return baseUrl;
            }

            public void setBaseUrl(String baseUrl) {
                this.baseUrl = baseUrl;
            }

            public boolean isServiceToken() {
                return serviceToken;
            }

            public void setServiceToken(boolean serviceToken) {
                this.serviceToken = serviceToken;
            }
        }
    }

    public static class Observability {
        /** HTTP header carrying the correlation id in and out of the service. */
        private String correlationHeader = "X-Correlation-Id";

        public String getCorrelationHeader() {
            return correlationHeader;
        }

        public void setCorrelationHeader(String correlationHeader) {
            this.correlationHeader = correlationHeader;
        }
    }
}
