package org.linlinjava.announce.cqrs.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code announce.service-token.*}: whether this service refuses HTTP requests that did not pass an edge, and the
 * secret it shares with the edges.
 */
@ConfigurationProperties(prefix = "announce.service-token")
public class ServiceTokenProperties {

    public enum Mode {
        /** No check: every request is accepted (tests, a service run on its own). */
        OFF,
        /** The token is checked and a failure is logged, but the request is served. For rolling the check out. */
        LOG,
        /** A request without a fresh, correctly signed token is answered 401. */
        ENFORCE
    }

    /**
     * {@code off}, {@code log} or {@code enforce}. Kept as text because an unquoted YAML {@code off} is read as the
     * boolean {@code false}; both spellings mean {@link Mode#OFF}.
     */
    private String mode = "off";
    /** Shared HMAC secret ({@code ANNOUNCE_SERVICE_SECRET}); required in the log and enforce modes and for minting. */
    private String secret;
    /** Name put into the tokens this service mints for its own outbound calls; defaults to the application name. */
    private String service;
    /** How far the token's timestamp may be from this host's clock, in either direction. */
    private Duration maxSkew = Duration.ofSeconds(60);
    /** Path patterns served without a token (health checks reach the service directly). */
    private List<String> openPaths = new ArrayList<>(List.of("/actuator/**"));

    public Mode resolvedMode() {
        String value = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "", "off", "false" -> Mode.OFF;
            case "log" -> Mode.LOG;
            case "enforce" -> Mode.ENFORCE;
            default -> throw new IllegalStateException("announce.service-token.mode must be off, log or enforce, not '" + mode + "'");
        };
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public Duration getMaxSkew() {
        return maxSkew;
    }

    public void setMaxSkew(Duration maxSkew) {
        this.maxSkew = maxSkew;
    }

    public List<String> getOpenPaths() {
        return openPaths;
    }

    public void setOpenPaths(List<String> openPaths) {
        this.openPaths = openPaths;
    }
}
