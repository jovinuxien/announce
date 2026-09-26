package org.linlinjava.announce.gatewayadmin.web.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;

import java.net.URI;

/** RFC 7807 problems for the edge-hosted admin CRUD, in the shape of announce-management's handler. */
@RestControllerAdvice(basePackages = "org.linlinjava.announce.gatewayadmin.web.admin")
public class AdminApiExceptionHandler {

    @ExceptionHandler(EdgeErrors.NotFound.class)
    public ProblemDetail notFound(EdgeErrors.NotFound e) {
        return problem(HttpStatus.NOT_FOUND, "not-found", e.getMessage());
    }

    @ExceptionHandler(EdgeErrors.Conflict.class)
    public ProblemDetail conflict(EdgeErrors.Conflict e) {
        ProblemDetail p = problem(HttpStatus.CONFLICT, e.code(), e.getMessage());
        p.setProperty("code", e.code());
        return p;
    }

    @ExceptionHandler({IllegalArgumentException.class, ServerWebInputException.class})
    public ProblemDetail badRequest(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", e instanceof ServerWebInputException s ? s.getReason() : e.getMessage());
    }

    static ProblemDetail problem(HttpStatus status, String type, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(status.getReasonPhrase());
        p.setType(URI.create("urn:announce:admin:" + type));
        return p;
    }
}
