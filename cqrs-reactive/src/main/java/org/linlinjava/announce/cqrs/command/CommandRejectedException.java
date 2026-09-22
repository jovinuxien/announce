package org.linlinjava.announce.cqrs.command;

import org.springframework.http.HttpStatus;

/**
 * Thrown by a command handler when a business rule rejects the command. Mapped to a problem detail with the
 * given status (409 Conflict by default) by {@link CqrsProblemDetailsHandler}.
 */
public class CommandRejectedException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public CommandRejectedException(String message) {
        this(HttpStatus.CONFLICT, null, message);
    }

    public CommandRejectedException(HttpStatus status, String message) {
        this(status, null, message);
    }

    /**
     * @param status  the HTTP status of the problem detail
     * @param code    a stable machine-readable code exposed as the {@code code} property, may be {@code null}
     * @param message the human-readable detail
     */
    public CommandRejectedException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
