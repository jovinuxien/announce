package org.linlinjava.announce.gatewayapi.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.MissingRequestValueException;
import org.springframework.web.server.ServerWebInputException;

/** Maps the edge's own auth failures to RFC 7807 problem details, like the backends do. */
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(AuthException.class)
    ProblemDetail auth(AuthException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
        p.setTitle(e.status().getReasonPhrase());
        return p;
    }

    @ExceptionHandler(WebExchangeBindException.class)
    ProblemDetail invalid(WebExchangeBindException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "validation failed");
        p.setTitle("Bad Request");
        p.setProperty("errors", e.getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).toList());
        return p;
    }

    @ExceptionHandler({ServerWebInputException.class, MissingRequestValueException.class})
    ProblemDetail input(ServerWebInputException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getReason());
        p.setTitle("Bad Request");
        return p;
    }
}
