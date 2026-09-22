package org.linlinjava.announce.cqrs.command;

import com.opencqrs.framework.CqrsFrameworkException;
import com.opencqrs.framework.client.ConcurrencyException;
import com.opencqrs.framework.command.CommandSubjectAlreadyExistsException;
import com.opencqrs.framework.command.CommandSubjectConditionViolatedException;
import com.opencqrs.framework.command.CommandSubjectDoesNotExistException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Maps command-side failures to RFC 7807 problem details (WebFlux):
 * <table>
 *   <tr><th>exception</th><th>status</th></tr>
 *   <tr><td>{@link CommandSubjectDoesNotExistException}</td><td>404 Not Found</td></tr>
 *   <tr><td>{@link CommandSubjectAlreadyExistsException}</td><td>409 Conflict</td></tr>
 *   <tr><td>other {@link CommandSubjectConditionViolatedException}</td><td>409 Conflict</td></tr>
 *   <tr><td>{@link ConcurrencyException} (subject changed while handling)</td><td>409 Conflict</td></tr>
 *   <tr><td>{@link DuplicateCommandException}</td><td>409 Conflict</td></tr>
 *   <tr><td>{@link CommandRejectedException}</td><td>its status (409 by default)</td></tr>
 *   <tr><td>{@link IllegalArgumentException}</td><td>400 Bad Request</td></tr>
 *   <tr><td>{@link CqrsFrameworkException.TransientException} (EventSourcingDB unavailable)</td><td>503 Service Unavailable</td></tr>
 *   <tr><td>other {@link CqrsFrameworkException}</td><td>500 Internal Server Error</td></tr>
 * </table>
 * The {@code type} of every problem is {@code urn:announce:cqrs:<kind>} and the {@code subject} property
 * carries the aggregate subject when known.
 */
@RestControllerAdvice
public class CqrsProblemDetailsHandler {

    private static final Logger log = LoggerFactory.getLogger(CqrsProblemDetailsHandler.class);
    private static final String TYPE_PREFIX = "urn:announce:cqrs:";

    @ExceptionHandler(CommandSubjectDoesNotExistException.class)
    public ProblemDetail subjectDoesNotExist(CommandSubjectDoesNotExistException e) {
        return problem(HttpStatus.NOT_FOUND, "subject-not-found", e.getMessage(), e.getCommand().getSubject());
    }

    @ExceptionHandler(CommandSubjectAlreadyExistsException.class)
    public ProblemDetail subjectAlreadyExists(CommandSubjectAlreadyExistsException e) {
        return problem(HttpStatus.CONFLICT, "subject-already-exists", e.getMessage(), e.getCommand().getSubject());
    }

    @ExceptionHandler(CommandSubjectConditionViolatedException.class)
    public ProblemDetail subjectConditionViolated(CommandSubjectConditionViolatedException e) {
        return problem(HttpStatus.CONFLICT, "subject-condition-violated", e.getMessage(), e.getCommand().getSubject());
    }

    @ExceptionHandler(ConcurrencyException.class)
    public ProblemDetail concurrentModification(ConcurrencyException e) {
        return problem(HttpStatus.CONFLICT, "concurrent-modification",
                "The subject was modified while the command was being handled; retry the command", null);
    }

    @ExceptionHandler(DuplicateCommandException.class)
    public ProblemDetail duplicateCommand(DuplicateCommandException e) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "duplicate-command", e.getMessage(), e.getSubject());
        problem.setProperty("commandId", e.getCommandId());
        problem.setProperty("inFlight", e.isInFlight());
        return problem;
    }

    @ExceptionHandler(CommandRejectedException.class)
    public ProblemDetail commandRejected(CommandRejectedException e) {
        ProblemDetail problem = problem(e.getStatus(), "command-rejected", e.getMessage(), null);
        if (e.getCode() != null) {
            problem.setProperty("code", e.getCode());
        }
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalidCommand(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-command", e.getMessage(), null);
    }

    @ExceptionHandler(CqrsFrameworkException.TransientException.class)
    public ProblemDetail transientFailure(CqrsFrameworkException.TransientException e) {
        log.warn("Transient CQRS failure: {}", e.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "event-store-unavailable",
                "The event store is temporarily unavailable; retry the command", null);
    }

    @ExceptionHandler(CqrsFrameworkException.class)
    public ProblemDetail frameworkFailure(CqrsFrameworkException e) {
        log.error("CQRS framework failure", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "framework-failure", "The command could not be handled", null);
    }

    static ProblemDetail problem(HttpStatus status, String kind, String detail, String subject) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + kind));
        problem.setTitle(status.getReasonPhrase());
        if (subject != null) {
            problem.setProperty("subject", subject);
        }
        return problem;
    }
}
